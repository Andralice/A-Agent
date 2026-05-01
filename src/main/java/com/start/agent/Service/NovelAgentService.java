package com.start.agent.service;

import com.start.agent.agent.NovelGenerationAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Chapter;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.Novel;
import com.start.agent.model.WritingPipeline;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NovelAgentService {
    private static final int INIT_CHAPTERS = 5;
    private static final Random RANDOM = new Random();
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile("^#?\\s*(第[\\d一二三四五六七八九十百千零]+章)(?:[:：\\s-]*(.*))?$");

    private final NapCatMessageService messageService;
    private final NovelGenerationAgent generationAgent;
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final GenerationLogService generationLogService;
    private final UserStatisticsService userStatisticsService;
    private final CharacterProfileService characterProfileService;
    private final EntityConsistencyService entityConsistencyService;
    private final StoryMemoryService storyMemoryService;
    private final ConsistencyAlertService consistencyAlertService;
    private final ChapterFactService chapterFactService;
    private final PlotSnapshotService plotSnapshotService;
    private final RegenerationTaskGuardService regenerationTaskGuardService;
    private final NovelMessageFormatter novelMessageFormatter;
    private final NovelExportService novelExportService;
    private final int defaultAutoContinueTarget;
    private final int maxAutoContinueTarget;
    private final ObjectMapper objectMapper;

    public NovelAgentService(NapCatMessageService messageService, NovelGenerationAgent generationAgent, NovelRepository novelRepository,
                             ChapterRepository chapterRepository, GenerationLogService generationLogService, UserStatisticsService userStatisticsService,
                             CharacterProfileService characterProfileService, EntityConsistencyService entityConsistencyService,
                             StoryMemoryService storyMemoryService,
                             ConsistencyAlertService consistencyAlertService, ChapterFactService chapterFactService,
                             PlotSnapshotService plotSnapshotService,
                             RegenerationTaskGuardService regenerationTaskGuardService,
                             @Value("${novel.auto-continue.default-target:20}") int defaultAutoContinueTarget,
                             @Value("${novel.auto-continue.max-target:200}") int maxAutoContinueTarget,
                             NovelMessageFormatter novelMessageFormatter, NovelExportService novelExportService,
                             ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.generationAgent = generationAgent;
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.generationLogService = generationLogService;
        this.userStatisticsService = userStatisticsService;
        this.characterProfileService = characterProfileService;
        this.entityConsistencyService = entityConsistencyService;
        this.storyMemoryService = storyMemoryService;
        this.consistencyAlertService = consistencyAlertService;
        this.chapterFactService = chapterFactService;
        this.plotSnapshotService = plotSnapshotService;
        this.regenerationTaskGuardService = regenerationTaskGuardService;
        this.defaultAutoContinueTarget = Math.max(1, defaultAutoContinueTarget);
        this.maxAutoContinueTarget = Math.max(this.defaultAutoContinueTarget, Math.max(1, maxAutoContinueTarget));
        this.novelMessageFormatter = novelMessageFormatter;
        this.novelExportService = novelExportService;
        this.objectMapper = objectMapper;
    }

    public void processAndSend(Long groupId, String topic) { processAndSend(groupId, topic, null, WritingPipeline.POWER_FANTASY); }

    public void processAndSend(Long groupId, String topic, String setting) {
        processAndSend(groupId, topic, setting, WritingPipeline.POWER_FANTASY);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline) {
        Long novelId = null;
        try {
            Novel novel = createNovel(groupId, topic, setting, pipeline);
            novelId = novel.getId();
            WritingPipeline effectivePipeline = resolvePipeline(novel);
            String outline = generationAgent.generateOutline(topic, setting, effectivePipeline);
            String profile = generationAgent.generateCharacterProfile(topic, setting, effectivePipeline);
            generationLogService.saveGenerationLog(novelId, null, "outline", len(outline), 0, "success", null);
            updateNovelDescription(novelId, outline);
            int savedProfiles = persistCharacterProfilesStrict(novelId, topic, setting, effectivePipeline, profile);
            generationLogService.saveGenerationLog(novelId, null, "character_profile", len(profile), 0,
                    savedProfiles > 0 ? "success" : "failed",
                    savedProfiles > 0 ? null : "strict json parse failed");
            userStatisticsService.updateGroupStatistics(groupId, 0, 0, true);
            safeSend(groupId, "Novel created: " + novel.getTitle());
            for (int i = 1; i <= INIT_CHAPTERS; i++) {
                Chapter chapter = gen(novel, i, setting, null);
                userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
                safeSend(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
            }
        } catch (Exception e) {
            log.error("create novel failed", e);
            generationLogService.saveGenerationLog(novelId, null, "error", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "Novel generation failed: " + e.getMessage());
        }
    }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter) { continueChapter(groupId, novelId, requestedChapter, null); }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting) {
        continueChapterInternal(groupId, novelId, requestedChapter, chapterSetting, true);
    }

    private void continueChapterInternal(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting, boolean useTaskGuard) {
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
            Novel novel = opt.get();
            List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
            int num = requestedChapter == null ? chapters.size() + 1 : requestedChapter;
            Optional<Chapter> old = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num);
            if (old.isPresent()) { safeSend(groupId, novelMessageFormatter.formatExistingChapter(old.get())); return; }
            if (useTaskGuard && !regenerationTaskGuardService.tryAcquireRange(novel.getId(), num, num)) {
                safeSend(groupId, "该章节正在重生/续写中，请稍后重试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            try {
                Chapter chapter = gen(novel, num, novel.getGenerationSetting(), chapterSetting);
                userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
                safeSend(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
            } finally {
                if (useTaskGuard) regenerationTaskGuardService.releaseRange(novel.getId(), num, num);
            }
        } catch (Exception e) {
            log.error("continue failed", e);
            generationLogService.saveGenerationLog(novelId == null ? null : novelId.longValue(), requestedChapter, "chapter", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "Chapter generation failed: " + e.getMessage());
        }
    }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount) { autoContinueChapter(groupId, novelId, targetChapterCount, null); }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount, String chapterSetting) {
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
            Novel novel = opt.get();
            int current = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId()).size();
            int target = targetChapterCount == null ? defaultAutoContinueTarget : targetChapterCount;
            if (target <= current) {
                safeSend(groupId, "目标章节数必须大于当前章节数（当前: " + current + "）");
                return;
            }
            if (target > maxAutoContinueTarget) {
                safeSend(groupId, "目标章节数超过安全上限（最大: " + maxAutoContinueTarget + "），请分批续写。");
                return;
            }
            int from = current + 1;
            if (!regenerationTaskGuardService.tryAcquireRange(novel.getId(), from, target)) {
                safeSend(groupId, "目标区间存在进行中的写入任务，请稍后再试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            try {
                for (int i = from; i <= target; i++) continueChapterInternal(groupId, novel.getId().intValue(), i, chapterSetting, false);
            } finally {
                regenerationTaskGuardService.releaseRange(novel.getId(), from, target);
            }
        } catch (Exception e) {
            log.error("auto continue failed", e);
            safeSend(groupId, "Auto continue failed: " + e.getMessage());
        }
    }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber) { regenerateChapter(groupId, novelId, chapterNumber, null); }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber, String newSetting) {
        regenerateChapterRange(groupId, novelId, chapterNumber, chapterNumber, newSetting);
    }

    public void regenerateChapterRange(Long groupId, Integer novelId, Integer startChapter, Integer endChapter, String newSetting) {
        try {
            if (startChapter == null || endChapter == null || startChapter <= 0 || endChapter <= 0) {
                safeSend(groupId, "章节号必须大于0");
                return;
            }
            int from = Math.min(startChapter, endChapter);
            int to = Math.max(startChapter, endChapter);
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
            Novel novel = opt.get();
            if (!regenerationTaskGuardService.tryAcquireRange(novel.getId(), from, to)) {
                safeSend(groupId, "该区间章节正在重生中，请稍后再试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            try {
                for (int chapterNum = from; chapterNum <= to; chapterNum++) {
                    Chapter chapter = gen(novel, chapterNum, novel.getGenerationSetting(), newSetting);
                    safeSend(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
                }
                safeSend(groupId, "区间重生完成: 第" + from + "章 到 第" + to + "章");
            } finally {
                regenerationTaskGuardService.releaseRange(novel.getId(), from, to);
            }
        } catch (Exception e) {
            log.error("regenerate chapter failed", e);
            safeSend(groupId, "Regenerate chapter failed: " + e.getMessage());
        }
    }

    public void repairCharacterProfiles(Long groupId, Integer novelId, boolean forceRegenerate) {
        repairCharacterProfiles(groupId, novelId, CharacterRepairOptions.simple(forceRegenerate));
    }

    public void repairCharacterProfiles(Long groupId, Integer novelId, CharacterRepairOptions options) {
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) {
                safeSend(groupId, "Novel not found");
                return;
            }
            Novel novel = opt.get();
            CharacterRepairOptions effectiveOptions = options == null ? CharacterRepairOptions.simple(false) : options;
            boolean hasUsableProfiles = characterProfileService.hasUsableProfiles(novel.getId());
            if (hasUsableProfiles && !effectiveOptions.forceRegenerate) {
                safeSend(groupId, "角色设定已存在并可用，已执行脏数据修复，无需重建。");
                return;
            }

            WritingPipeline pipeline = resolvePipeline(novel);
            boolean repaired = false;
            String lastError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    String repairSetting = buildCharacterRepairSetting(novel, effectiveOptions);
                    String profileText = generationAgent.generateCharacterProfile(novel.getTopic(), repairSetting, pipeline);
                    boolean replaceAll = effectiveOptions.rebuildMode == RebuildMode.ALL;
                    int savedProfiles = characterProfileService.saveCharacterProfilesJsonWithMode(
                            novel.getId(),
                            profileText,
                            replaceAll,
                            effectiveOptions.targetCharacterNames
                    );
                    if (savedProfiles > 0 && characterProfileService.hasUsableProfiles(novel.getId())) {
                        generationLogService.saveGenerationLog(novel.getId(), null, "character_profile_repair", len(profileText), 0, "success", null);
                        safeSend(groupId, "角色设定修复完成" + (effectiveOptions.forceRegenerate ? "（强制重建）" : "（自动补全）"));
                        repaired = true;
                        break;
                    }
                    lastError = "解析结果无有效角色名";
                } catch (Exception e) {
                    lastError = e.getMessage();
                }
            }
            if (!repaired) {
                generationLogService.saveGenerationLog(novel.getId(), null, "character_profile_repair", 0, 0, "failed", lastError);
                safeSend(groupId, "角色设定修复失败：生成结果质量不足，请稍后重试。");
            }
        } catch (Exception e) {
            log.error("repair character profiles failed", e);
            generationLogService.saveGenerationLog(novelId == null ? null : novelId.longValue(), null, "character_profile_repair", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "角色设定修复失败: " + e.getMessage());
        }
    }

    private String buildCharacterRepairSetting(Novel novel, CharacterRepairOptions options) {
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        StringBuilder builder = new StringBuilder();
        if (novel.getGenerationSetting() != null && !novel.getGenerationSetting().isBlank()) {
            builder.append(novel.getGenerationSetting().trim()).append("\n\n");
        }
        builder.append("【角色修复模式】\n");
        builder.append("- 重建范围: ").append(options.rebuildMode == RebuildMode.PARTIAL ? "部分重建" : "全量重建").append("\n");
        if (options.characterContextHint != null && !options.characterContextHint.isBlank()) {
            builder.append("- 角色上下文约束: ").append(options.characterContextHint.trim()).append("\n");
        }
        if (options.targetCharacterNames != null && !options.targetCharacterNames.isEmpty()) {
            builder.append("- 指定重建角色: ").append(String.join("、", options.targetCharacterNames)).append("\n");
        }
        if (options.extraHint != null && !options.extraHint.isBlank()) {
            builder.append("- 附加要求: ").append(options.extraHint.trim()).append("\n");
        }
        builder.append("\n【已有大纲（必须参考）】\n").append(novel.getDescription() == null ? "无" : novel.getDescription()).append("\n");
        builder.append("\n【已有章节摘要（必须参考）】\n").append(summary(chapters));
        builder.append("\n【已有角色档案（必须参考，保持名称连续性）】\n").append(characterProfileService.getCharacterProfileFromDatabase(novel.getId()));
        return builder.toString();
    }

    private Chapter gen(Novel novel, int num, String novelSetting, String chapterSetting) {
        String profile = characterProfileService.getStableCharacterProfileBlock(novel.getId());
        List<Chapter> allChapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        String previousContent = num <= 1 ? "" : chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num - 1).map(Chapter::getContent).orElse("");
        String nextContent = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num + 1).map(Chapter::getContent).orElse("");
        List<CharacterProfile> characterProfiles = characterProfileService.getProfiles(novel.getId());
        List<EntityConsistencyService.LockRule> lockRules = entityConsistencyService.buildStrongLockRules(characterProfiles);
        List<String> lockedNames = lockRules.stream().map(EntityConsistencyService.LockRule::getName).toList();
        String immutableConstraints = entityConsistencyService.buildImmutableConstraints(lockRules);
        String longTermMemory = storyMemoryService.buildStoryMemory(allChapters, lockedNames);
        String factMemory = chapterFactService.buildFactMemory(novel.getId(), 15);
        String snapshotMemory = plotSnapshotService.getLatestSnapshotBlock(novel.getId());
        WritingPipeline pipeline = resolvePipeline(novel);
        String content = generationAgent.generateChapter(
                novel.getDescription(),
                num,
                previousContent,
                nextContent,
                profile,
                summary(allChapters) + "\n\n" + longTermMemory + "\n\n" + factMemory + "\n\n" + snapshotMemory,
                novelSetting,
                chapterSetting,
                immutableConstraints,
                pipeline
        );
        if (content == null || content.trim().isEmpty()) throw new RuntimeException("AI生成失败：返回内容为空");
        String issueHint = entityConsistencyService.detectNameConsistencyIssue(previousContent, content, lockRules);
        String snapshotIssueHint = plotSnapshotService.detectSnapshotDrift(novel.getId(), content, lockedNames);
        if (snapshotIssueHint != null) {
            consistencyAlertService.saveAlert(novel.getId(), num, "snapshot_drift", "medium", snapshotIssueHint, false, false);
        }
        String combinedIssueHint = combineIssueHint(issueHint, snapshotIssueHint);
        if (combinedIssueHint != null) {
            log.warn("第{}章检测到一致性风险，触发自动修复: {}", num, combinedIssueHint);
            if (issueHint != null) {
                consistencyAlertService.saveAlert(novel.getId(), num, "name_consistency", "high", issueHint, true, false);
            }
            String revised = generationAgent.reviseChapterForConsistency(content, immutableConstraints, combinedIssueHint);
            if (revised != null && !revised.trim().isEmpty()) {
                content = revised;
                String secondIssue = entityConsistencyService.detectNameConsistencyIssue(previousContent, content, lockRules);
                String secondSnapshotIssue = plotSnapshotService.detectSnapshotDrift(novel.getId(), content, lockedNames);
                boolean fixSuccess = secondIssue == null && secondSnapshotIssue == null;
                consistencyAlertService.saveAlert(novel.getId(), num, "name_consistency_fix", fixSuccess ? "info" : "high",
                        fixSuccess ? "自动修复成功" : "自动修复后仍存在一致性风险", true, fixSuccess);
            }
        }
        ChapterDraft draft = normalizeChapterDraft(content, num);
        ChapterSidecar sidecar = extractChapterSidecarSafe(novel, num, draft.content, pipeline, lockedNames);
        if (sidecar != null && sidecar.title != null && !sidecar.title.isBlank()) {
            draft = normalizeChapterDraft(sidecar.title + "\n" + draft.content, num);
        }
        chapterFactService.rebuildFactsForChapter(
                novel.getId(),
                num,
                draft.content,
                lockedNames,
                sidecar == null ? List.of() : sidecar.facts,
                sidecar == null ? null : sidecar.continuityAnchor
        );
        plotSnapshotService.refreshSnapshotIfNeeded(novel.getId(), num, lockedNames);
        generationLogService.saveGenerationLog(novel.getId(), num, "chapter", len(draft.content), 0, "success", null);
        return saveChapter(novel.getId(), num, draft.title, draft.content, chapterSetting);
    }

    public void listNovels(Long groupId) {
        StringBuilder msg = new StringBuilder("Novel list:\n");
        for (Novel n : novelRepository.findByGroupId(groupId)) msg.append(n.getId()).append(" ").append(n.getTitle()).append("\n");
        safeSend(groupId, msg.toString());
    }

    public void showOutline(Long groupId, Integer novelId) {
        Optional<Novel> n = resolve(groupId, novelId);
        safeSend(groupId, n.map(x -> x.getTitle() + "\n" + x.getDescription()).orElse("Novel not found"));
    }

    public void readNovel(Long groupId, Integer novelId, Integer chapterNum) {
        Optional<Novel> n = resolve(groupId, novelId);
        if (n.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
        List<Chapter> cs = chapterRepository.findByNovelIdOrderByChapterNumberAsc(n.get().getId());
        Chapter c = chapterNum == null ? (cs.isEmpty() ? null : cs.get(0)) : chapterRepository.findByNovelIdAndChapterNumber(n.get().getId(), chapterNum).orElse(null);
        safeSend(groupId, c == null ? "Chapter not found" : c.getTitle() + "\n" + c.getContent());
    }

    @Transactional
    public void updateNovelDescription(Long novelId, String description) {
        Novel n = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("novel not found: " + novelId));
        n.setDescription(description);
        novelRepository.save(n);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic) { return createNovel(groupId, topic, null); }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting) {
        return createNovel(groupId, topic, setting, WritingPipeline.POWER_FANTASY);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline) {
        Novel n = new Novel();
        n.setTitle(title(topic));
        n.setTopic(topic);
        n.setGenerationSetting(norm(setting));
        n.setGroupId(groupId);
        n.setDescription("AI generated novel");
        n.setWritingPipeline((pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline).name());
        return novelRepository.save(n);
    }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content) { return saveChapter(novelId, num, title, content, null); }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content, String setting) {
        Optional<Chapter> existing = chapterRepository.findByNovelIdAndChapterNumber(novelId, num);
        if (existing.isPresent()) {
            Chapter chapter = existing.get();
            chapter.setTitle(title); chapter.setContent(content); chapter.setGenerationSetting(norm(setting));
            return chapterRepository.save(chapter);
        }
        Chapter c = new Chapter();
        c.setNovelId(novelId); c.setChapterNumber(num); c.setTitle(title); c.setContent(content); c.setGenerationSetting(norm(setting));
        return chapterRepository.save(c);
    }

    public List<Novel> getAllNovels() {
        return novelRepository.findAll();
    }

    public List<Chapter> getChaptersByNovelId(Long novelId) {
        return chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
    }

    public String exportNovelToTxt(Long novelId) {
        return novelExportService.exportNovelToTxt(novelId);
    }

    @Transactional
    public void updateNovelPipeline(Long novelId, WritingPipeline pipeline) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("novel not found: " + novelId));
        novel.setWritingPipeline((pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline).name());
        novelRepository.save(novel);
    }

    private WritingPipeline resolvePipeline(Novel novel) {
        if (novel == null || novel.getWritingPipeline() == null || novel.getWritingPipeline().isBlank()) {
            return WritingPipeline.POWER_FANTASY;
        }
        try {
            return WritingPipeline.valueOf(novel.getWritingPipeline());
        } catch (Exception ignore) {
            return WritingPipeline.POWER_FANTASY;
        }
    }

    private int persistCharacterProfilesStrict(Long novelId, String topic, String setting, WritingPipeline pipeline, String firstResult) {
        int saved = characterProfileService.saveCharacterProfilesJsonOnly(novelId, firstResult);
        if (saved > 0) return saved;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String retryPromptSetting = (setting == null ? "" : setting + "\n") + "【输出要求】必须返回严格JSON对象，字段仅包含 characters/name/type/want/fear/knowledge/summary。";
            String retryResult = generationAgent.generateCharacterProfile(topic, retryPromptSetting, pipeline);
            saved = characterProfileService.saveCharacterProfilesJsonOnly(novelId, retryResult);
            if (saved > 0) return saved;
        }
        return 0;
    }

    private Optional<Novel> resolve(Long groupId, Integer novelId) { return novelId == null ? latest(groupId) : novelRepository.findById(novelId.longValue()); }
    private Optional<Novel> latest(Long groupId) { return novelRepository.findByGroupId(groupId).stream().findFirst(); }
    private String norm(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
    private int len(String s) { return s == null ? 0 : s.length(); }
    private void safeSend(Long g, String m) { try { messageService.sendGroupMessage(g, m); } catch (Exception e) { log.error("send failed", e); } }
    private String title(String topic) { List<String> t = Arrays.asList("%s: %s", "%s legend", "%s rise"); List<String> s = Arrays.asList("Awakening", "Rebirth", "Fantasy"); return String.format(t.get(RANDOM.nextInt(t.size())), topic, s.get(RANDOM.nextInt(s.size()))); }
    private String summary(List<Chapter> cs) { if (cs == null || cs.isEmpty()) return ""; StringBuilder sb = new StringBuilder(); int from = Math.max(0, cs.size() - 3); for (int i = from; i < cs.size(); i++) { String c = cs.get(i).getContent() == null ? "" : cs.get(i).getContent(); sb.append(cs.get(i).getTitle()).append(": ").append(c, 0, Math.min(200, c.length())).append("\n"); } return sb.toString(); }
    private String combineIssueHint(String first, String second) {
        if (first == null || first.isBlank()) return (second == null || second.isBlank()) ? null : second;
        if (second == null || second.isBlank()) return first;
        return first + " " + second;
    }

    private ChapterSidecar extractChapterSidecarSafe(Novel novel, int chapterNumber, String content, WritingPipeline pipeline, List<String> lockedNames) {
        try {
            String json = generationAgent.generateChapterSidecar(content, novel.getDescription(), chapterNumber, pipeline);
            JsonNode root = objectMapper.readTree(extractJsonBody(json));
            ChapterSidecar sidecar = new ChapterSidecar();
            sidecar.title = sanitizeShort(root.path("title").asText(null), 60);
            sidecar.continuityAnchor = sanitizeShort(root.path("continuity_anchor").asText(null), 200);
            sidecar.facts = new ArrayList<>();
            JsonNode factsNode = root.path("facts");
            if (factsNode.isArray()) {
                for (JsonNode node : factsNode) {
                    String fact = sanitizeShort(node.asText(null), 200);
                    if (fact != null && !fact.isBlank()) sidecar.facts.add(fact);
                }
            }
            if (sidecar.title != null && !sidecar.title.startsWith("第")) {
                sidecar.title = "第" + chapterNumber + "章 " + sidecar.title;
            }
            if (sidecar.facts.isEmpty() && (sidecar.continuityAnchor == null || sidecar.continuityAnchor.isBlank())) return null;
            return sidecar;
        } catch (Exception e) {
            log.warn("章节sidecar提取失败，第{}章，继续使用正文解析: {}", chapterNumber, e.getMessage());
            return null;
        }
    }

    private String extractJsonBody(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```\\s*$", "");
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private String sanitizeShort(String text, int maxLen) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replace("\n", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private ChapterDraft normalizeChapterDraft(String rawContent, int chapterNumber) {
        String[] lines = rawContent.trim().split("\\n");
        String defaultTitle = "第" + chapterNumber + "章";
        if (lines.length == 0) return new ChapterDraft(defaultTitle, rawContent.trim());
        String firstLine = lines[0].trim().replaceFirst("^#\\s*", "");
        Matcher matcher = CHAPTER_TITLE_PATTERN.matcher(firstLine);
        if (matcher.matches()) {
            String prefix = matcher.group(1);
            String suffix = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String normalizedTitle = suffix.isEmpty() ? prefix : prefix + " " + suffix;
            String cleanBody = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length)).trim();
            return new ChapterDraft(normalizedTitle, cleanBody.isEmpty() ? rawContent.trim() : cleanBody);
        }
        return new ChapterDraft(defaultTitle, rawContent.trim());
    }

    private static class ChapterDraft {
        private final String title;
        private final String content;
        private ChapterDraft(String title, String content) { this.title = title; this.content = content; }
    }

    private static class ChapterSidecar {
        private String title;
        private String continuityAnchor;
        private List<String> facts;
    }

    public static class CharacterRepairOptions {
        private final boolean forceRegenerate;
        private final RebuildMode rebuildMode;
        private final String characterContextHint;
        private final List<String> targetCharacterNames;
        private final String extraHint;

        public CharacterRepairOptions(boolean forceRegenerate, RebuildMode rebuildMode, String characterContextHint,
                                      List<String> targetCharacterNames, String extraHint) {
            this.forceRegenerate = forceRegenerate;
            this.rebuildMode = rebuildMode == null ? RebuildMode.ALL : rebuildMode;
            this.characterContextHint = characterContextHint;
            this.targetCharacterNames = targetCharacterNames == null ? List.of() : targetCharacterNames;
            this.extraHint = extraHint;
        }

        public static CharacterRepairOptions simple(boolean forceRegenerate) {
            return new CharacterRepairOptions(forceRegenerate, RebuildMode.ALL, null, List.of(), null);
        }
    }

    public enum RebuildMode {
        ALL,
        PARTIAL;

        public static RebuildMode from(String mode) {
            if (mode == null || mode.isBlank()) return ALL;
            return switch (mode.trim().toLowerCase()) {
                case "all" -> ALL;
                case "partial" -> PARTIAL;
                default -> throw new IllegalArgumentException("rebuildMode 仅支持 all 或 partial");
            };
        }
    }
}