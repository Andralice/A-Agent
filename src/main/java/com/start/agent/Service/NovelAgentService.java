package com.start.agent.service;

import com.start.agent.agent.NovelGenerationAgent;
import com.start.agent.agent.OutlineGenerationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.Novel;
import com.start.agent.model.NovelWritePhase;
import com.start.agent.model.PlotSnapshot;
import com.start.agent.model.WritingPipeline;
import com.start.agent.model.WritingStyleHints;
import com.start.agent.model.WritingStyleParamsSupport;
import com.start.agent.narrative.NarrativeEngineArtifactSink;
import com.start.agent.narrative.NarrativePhysicsMode;
import com.start.agent.narrative.NarrativeProfile;
import com.start.agent.narrative.NarrativeProfileResolver;
import com.start.agent.exception.ChapterGenerationAbortedException;
import com.start.agent.exception.NotFoundException;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说创作领域核心服务：开书/续写/重生、任务编排、章节与角色落库、与 QQ 推送等入口的业务封装。
 */
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
    private final GenerationTaskService generationTaskService;
    private final WritingProgressService writingProgressService;
    private final NewCharacterIngestService newCharacterIngestService;
    private final NovelMessageFormatter novelMessageFormatter;
    private final NovelExportService novelExportService;
    private final NarrativeProfileResolver narrativeProfileResolver;
    private final CharacterNarrativeStateService characterNarrativeStateService;
    private final int defaultAutoContinueTarget;
    private final int maxAutoContinueTarget;
    private final ObjectMapper objectMapper;
    private final boolean narrativeEngineEnabled;
    private final boolean narrativeM4CarryoverEnabled;
    private final boolean narrativeM7ArtifactEnabled;
    private final boolean narrativeM9CrosscutEnabled;

    public NovelAgentService(NapCatMessageService messageService, NovelGenerationAgent generationAgent, NovelRepository novelRepository,
                             ChapterRepository chapterRepository, GenerationLogService generationLogService, UserStatisticsService userStatisticsService,
                             CharacterProfileService characterProfileService, EntityConsistencyService entityConsistencyService,
                             StoryMemoryService storyMemoryService,
                             ConsistencyAlertService consistencyAlertService, ChapterFactService chapterFactService,
                             PlotSnapshotService plotSnapshotService,
                             RegenerationTaskGuardService regenerationTaskGuardService,
                             @Lazy GenerationTaskService generationTaskService,
                             WritingProgressService writingProgressService,
                             NewCharacterIngestService newCharacterIngestService,
                             @Value("${novel.auto-continue.default-target:20}") int defaultAutoContinueTarget,
                             @Value("${novel.auto-continue.max-target:200}") int maxAutoContinueTarget,
                             NovelMessageFormatter novelMessageFormatter,                              NovelExportService novelExportService,
                             NarrativeProfileResolver narrativeProfileResolver,
                             CharacterNarrativeStateService characterNarrativeStateService,
                             ObjectMapper objectMapper,
                             @Value("${novel.narrative-engine.enabled:true}") boolean narrativeEngineEnabled,
                             @Value("${novel.narrative-engine.m4-carryover-enabled:true}") boolean narrativeM4CarryoverEnabled,
                             @Value("${novel.narrative-engine.m7-artifact-enabled:true}") boolean narrativeM7ArtifactEnabled,
                             @Value("${novel.narrative-engine.m9-crosscut-enabled:false}") boolean narrativeM9CrosscutEnabled) {
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
        this.generationTaskService = generationTaskService;
        this.writingProgressService = writingProgressService;
        this.newCharacterIngestService = newCharacterIngestService;
        this.defaultAutoContinueTarget = Math.max(1, defaultAutoContinueTarget);
        this.maxAutoContinueTarget = Math.max(this.defaultAutoContinueTarget, Math.max(1, maxAutoContinueTarget));
        this.novelMessageFormatter = novelMessageFormatter;
        this.novelExportService = novelExportService;
        this.narrativeProfileResolver = narrativeProfileResolver;
        this.characterNarrativeStateService = characterNarrativeStateService;
        this.objectMapper = objectMapper;
        this.narrativeEngineEnabled = narrativeEngineEnabled;
        this.narrativeM4CarryoverEnabled = narrativeM4CarryoverEnabled;
        this.narrativeM7ArtifactEnabled = narrativeM7ArtifactEnabled;
        this.narrativeM9CrosscutEnabled = narrativeM9CrosscutEnabled;
    }

    public void processAndSend(Long groupId, String topic) { processAndSend(groupId, topic, null, WritingPipeline.POWER_FANTASY); }

    public void processAndSend(Long groupId, String topic, String setting) {
        processAndSend(groupId, topic, setting, WritingPipeline.POWER_FANTASY);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline) {
        processAndSend(groupId, topic, setting, pipeline, false, null);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled) {
        processAndSend(groupId, topic, setting, pipeline, hotMemeEnabled, null, null, null, null, null);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                               String writingStyleParamsJson) {
        processAndSend(groupId, topic, setting, pipeline, hotMemeEnabled, writingStyleParamsJson, null, null, null, null);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                               String writingStyleParamsJson, String serializationPlatform, String creatorNote) {
        processAndSend(groupId, topic, setting, pipeline, hotMemeEnabled, writingStyleParamsJson, serializationPlatform, creatorNote, null, null);
    }

    public void processAndSend(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                               String writingStyleParamsJson, String serializationPlatform, String creatorNote,
                               Integer outlineDetailedPrefixChapters, Integer outlineMinRoadmapChapters) {
        Long novelId = null;
        try {
            Novel novel = createNovel(groupId, topic, setting, pipeline, hotMemeEnabled, writingStyleParamsJson, serializationPlatform, creatorNote,
                    outlineDetailedPrefixChapters, outlineMinRoadmapChapters);
            novelId = novel.getId();
            writingProgressService.beginOperation(novelId, NovelWritePhase.INITIAL_BOOTSTRAP, 1, INIT_CHAPTERS);
            WritingPipeline effectivePipeline = resolvePipeline(novel);
            boolean hotMeme = novel.isHotMemeEnabled();
            WritingStyleHints styleHints = resolveStyleHints(novel);
            OutlineGenerationResult outlineResult = generationAgent.generateOutlineResult(topic, setting, effectivePipeline, hotMeme, styleHints,
                    novel.getOutlineDetailedPrefixChapters(), novel.getOutlineMinRoadmapChapters(), null);
            String outline = outlineResult.markdown();
            updateNovelOutline(novelId, outline, outlineResult.outlineGraphJson());
            String profile = generationAgent.generateCharacterProfile(topic, setting, effectivePipeline, hotMeme, styleHints,
                    outline, outlineResult.outlineGraphJson());
            generationLogService.saveGenerationLog(novelId, null, "outline", len(outline), 0, "success", null);
            int savedProfiles = persistCharacterProfilesStrict(novelId, topic, setting, effectivePipeline, profile, hotMeme, styleHints,
                    outline, outlineResult.outlineGraphJson());
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
        } finally {
            if (novelId != null) {
                try {
                    writingProgressService.finishOperation(novelId);
                } catch (Exception ex) {
                    log.warn("初创建工作台收尾失败 novelId={}", novelId, ex);
                }
            }
        }
    }

    /**
     * 可恢复新建：补齐大纲、角色设定与前若干章（幂等：已存在则跳过）。
     * 由 generation_task(INITIAL_BOOTSTRAP) 驱动。
     */
    public void bootstrapNovel(Long groupId, Long novelId, int targetChapters) {
        bootstrapNovel(groupId, novelId, targetChapters, null);
    }

    /**
     * @param generationTaskId 非 null 时协作式响应任务取消（大纲/角色/章节步骤之间检查）。
     */
    public void bootstrapNovel(Long groupId, Long novelId, int targetChapters, Long generationTaskId) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("novel not found: " + novelId));
        int target = Math.max(1, targetChapters);
        WritingPipeline pipeline = resolvePipeline(novel);
        String setting = novel.getGenerationSetting();

        // Outline
        boolean outlineReady = novel.getDescription() != null && !novel.getDescription().isBlank()
                && !"AI generated novel".equals(novel.getDescription()) && !"AI生成的爽文小说".equals(novel.getDescription());
        WritingStyleHints styleHints = resolveStyleHints(novel);
        String outlineMdForChars = novel.getDescription();
        String outlineGjForChars = novel.getOutlineGraphJson();
        if (!outlineReady) {
            if (generationTaskId != null && generationTaskService.isTaskCancelled(generationTaskId)) {
                return;
            }
            OutlineGenerationResult outlineResult = generationAgent.generateOutlineResult(novel.getTopic(), setting, pipeline, novel.isHotMemeEnabled(), styleHints,
                    novel.getOutlineDetailedPrefixChapters(), novel.getOutlineMinRoadmapChapters(), null);
            outlineMdForChars = outlineResult.markdown();
            outlineGjForChars = outlineResult.outlineGraphJson();
            generationLogService.saveGenerationLog(novelId, null, "outline", len(outlineMdForChars), 0, "success", null);
            updateNovelOutline(novelId, outlineMdForChars, outlineGjForChars);
        }

        // Profiles
        if (!characterProfileService.hasUsableProfiles(novelId)) {
            if (generationTaskId != null && generationTaskService.isTaskCancelled(generationTaskId)) {
                return;
            }
            String profile = generationAgent.generateCharacterProfile(novel.getTopic(), setting, pipeline, novel.isHotMemeEnabled(), styleHints,
                    outlineMdForChars, outlineGjForChars);
            int savedProfiles = persistCharacterProfilesStrict(novelId, novel.getTopic(), setting, pipeline, profile, novel.isHotMemeEnabled(), styleHints,
                    outlineMdForChars, outlineGjForChars);
            generationLogService.saveGenerationLog(novelId, null, "character_profile", len(profile), 0,
                    savedProfiles > 0 ? "success" : "failed",
                    savedProfiles > 0 ? null : "strict json parse failed");
        }

        // Chapters
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
        Set<Integer> exists = new HashSet<>();
        for (Chapter c : chapters) if (c != null && c.getChapterNumber() != null) exists.add(c.getChapterNumber());
        for (int i = 1; i <= target; i++) {
            if (exists.contains(i)) continue;
            if (generationTaskId != null && generationTaskService.isTaskCancelled(generationTaskId)) {
                return;
            }
            Chapter chapter = gen(novel, i, setting, null, generationTaskId);
            userStatisticsService.updateGroupStatistics(novel.getGroupId(), 1, len(chapter.getContent()), false);
            safeSend(novel.getGroupId(), novelMessageFormatter.formatNovelMessage(novel, chapter));
        }
    }

    /**
     * 重新生成全书大纲（覆盖 {@link Novel#getDescription()}）。可选更新本书的大纲规划章参；已写正文不会自动改写，可能与新旧大纲不一致。
     * 与章节类生成互斥：先本进程大纲区间锁，再 DB 租约；AI 调用不在长事务中。
     */
    public void regenerateOutline(Long novelId, String userHint, Integer outlineDetailedPrefixChapters, Integer outlineMinRoadmapChapters) {
        boolean outlineLock = false;
        Long leaseTaskId = null;
        try {
            if (!regenerationTaskGuardService.tryAcquireOutlineRegenerationLock(novelId)) {
                throw new IllegalStateException("本书章节写入进行中，请稍后再修改大纲");
            }
            outlineLock = true;
            leaseTaskId = generationTaskService.attachOutlineRegenerationLease(novelId);
            Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("novel not found: " + novelId));
            if (outlineDetailedPrefixChapters != null) {
                novel.setOutlineDetailedPrefixChapters(outlineDetailedPrefixChapters);
            }
            if (outlineMinRoadmapChapters != null) {
                novel.setOutlineMinRoadmapChapters(outlineMinRoadmapChapters);
            }
            novelRepository.save(novel);
            WritingPipeline pipeline = resolvePipeline(novel);
            WritingStyleHints styleHints = resolveStyleHints(novel);
            String setting = novel.getGenerationSetting();
            OutlineGenerationResult outlineResult = generationAgent.generateOutlineResult(novel.getTopic(), setting, pipeline, novel.isHotMemeEnabled(), styleHints,
                    novel.getOutlineDetailedPrefixChapters(), novel.getOutlineMinRoadmapChapters(),
                    userHint);
            updateNovelOutline(novelId, outlineResult.markdown(), outlineResult.outlineGraphJson());
            String logNote = userHint == null || userHint.isBlank() ? "outline_regenerate" : "outline_regenerate_with_hint";
            generationLogService.saveGenerationLog(novelId, null, "outline", len(outlineResult.markdown()), 0, "success", logNote);
        } finally {
            generationTaskService.releaseOutlineRegenerationLease(leaseTaskId);
            if (outlineLock) {
                regenerationTaskGuardService.releaseOutlineRegenerationLock(novelId);
            }
        }
    }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter) { continueChapter(groupId, novelId, requestedChapter, null); }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting) {
        continueChapterInternal(groupId, novelId, requestedChapter, chapterSetting, true, null);
    }

    /** 由 {@link GenerationTaskService} 传入 taskId，便于协作式取消。 */
    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting, Long generationTaskId) {
        continueChapterInternal(groupId, novelId, requestedChapter, chapterSetting, true, generationTaskId);
    }

    private void continueChapterInternal(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting, boolean useTaskGuard) {
        continueChapterInternal(groupId, novelId, requestedChapter, chapterSetting, useTaskGuard, null);
    }

    private void continueChapterInternal(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting, boolean useTaskGuard, Long generationTaskId) {
        boolean locked = false;
        boolean persistedWorkbench = false;
        Long persistedNovelPk = novelId == null ? null : novelId.longValue();
        int targetChapterNum = -1;
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
            Novel novel = opt.get();
            persistedNovelPk = novel.getId();
            try {
                generationTaskService.assertNoOutlineRegenerationLease(novel.getId());
            } catch (IllegalStateException e) {
                safeSend(groupId, e.getMessage());
                return;
            }
            List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
            targetChapterNum = requestedChapter == null ? chapters.size() + 1 : requestedChapter;
            Optional<Chapter> old = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), targetChapterNum);
            if (old.isPresent()) { safeSend(groupId, novelMessageFormatter.formatExistingChapter(old.get())); return; }
            if (useTaskGuard && !regenerationTaskGuardService.tryAcquireRange(novel.getId(), targetChapterNum, targetChapterNum)) {
                safeSend(groupId, "该章节正在重生/续写中，请稍后重试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            locked = useTaskGuard;
            if (useTaskGuard) {
                writingProgressService.beginOperation(novel.getId(), NovelWritePhase.SINGLE_CONTINUE, targetChapterNum, targetChapterNum);
                persistedWorkbench = true;
            }
            if (generationTaskId != null && generationTaskService.isTaskCancelled(generationTaskId)) {
                log.info("续写任务已取消，跳过本章 novelId={} chapter={}", persistedNovelPk, targetChapterNum);
                return;
            }
            Chapter chapter = gen(novel, targetChapterNum, novel.getGenerationSetting(), chapterSetting, generationTaskId);
            userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
            safeSend(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
        } catch (ChapterGenerationAbortedException e) {
            log.info("续写因任务取消而中止 novelId={} chapter={}", persistedNovelPk, targetChapterNum);
        } catch (Exception e) {
            log.error("continue failed", e);
            generationLogService.saveGenerationLog(persistedNovelPk, requestedChapter, "chapter", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "Chapter generation failed: " + e.getMessage());
        } finally {
            try {
                if (locked && persistedNovelPk != null && targetChapterNum > 0) {
                    regenerationTaskGuardService.releaseRange(persistedNovelPk, targetChapterNum, targetChapterNum);
                }
                if (persistedWorkbench && persistedNovelPk != null) {
                    writingProgressService.finishOperation(persistedNovelPk);
                }
            } catch (Exception ex) {
                log.warn("续写收尾失败 novelId={} chapter={}", persistedNovelPk, targetChapterNum, ex);
            }
        }
    }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount) { autoContinueChapter(groupId, novelId, targetChapterCount, null); }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount, String chapterSetting) {
        boolean lockedRange = false;
        boolean persistedWorkbench = false;
        Long nid = null;
        int rangeFrom = 0;
        int rangeTo = 0;
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) { safeSend(groupId, "Novel not found"); return; }
            Novel novel = opt.get();
            nid = novel.getId();
            try {
                generationTaskService.assertNoOutlineRegenerationLease(novel.getId());
            } catch (IllegalStateException e) {
                safeSend(groupId, e.getMessage());
                return;
            }
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
            rangeFrom = current + 1;
            rangeTo = target;
            if (!regenerationTaskGuardService.tryAcquireRange(novel.getId(), rangeFrom, rangeTo)) {
                safeSend(groupId, "目标区间存在进行中的写入任务，请稍后再试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            lockedRange = true;
            writingProgressService.beginOperation(novel.getId(), NovelWritePhase.AUTO_CONTINUE_RANGE, rangeFrom, rangeTo);
            persistedWorkbench = true;
            for (int i = rangeFrom; i <= rangeTo; i++) {
                continueChapterInternal(groupId, novel.getId().intValue(), i, chapterSetting, false);
            }
        } catch (Exception e) {
            log.error("auto continue failed", e);
            safeSend(groupId, "Auto continue failed: " + e.getMessage());
        } finally {
            try {
                if (lockedRange && nid != null && rangeFrom > 0 && rangeTo >= rangeFrom) {
                    regenerationTaskGuardService.releaseRange(nid, rangeFrom, rangeTo);
                }
                if (persistedWorkbench && nid != null) {
                    writingProgressService.finishOperation(nid);
                }
            } catch (Exception ex) {
                log.warn("自动续写收尾失败 novelId={} range={}-{}", nid, rangeFrom, rangeTo, ex);
            }
        }
    }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber) { regenerateChapter(groupId, novelId, chapterNumber, null); }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber, String newSetting) {
        regenerateChapterRange(groupId, novelId, chapterNumber, chapterNumber, newSetting, null);
    }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber, String newSetting, Long generationTaskId) {
        regenerateChapterRange(groupId, novelId, chapterNumber, chapterNumber, newSetting, generationTaskId);
    }

    public void regenerateChapterRange(Long groupId, Integer novelId, Integer startChapter, Integer endChapter, String newSetting) {
        regenerateChapterRange(groupId, novelId, startChapter, endChapter, newSetting, null);
    }

    public void regenerateChapterRange(Long groupId, Integer novelId, Integer startChapter, Integer endChapter, String newSetting, Long generationTaskId) {
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
            try {
                generationTaskService.assertNoOutlineRegenerationLease(novel.getId());
            } catch (IllegalStateException e) {
                safeSend(groupId, e.getMessage());
                return;
            }
            if (!regenerationTaskGuardService.tryAcquireRange(novel.getId(), from, to)) {
                safeSend(groupId, "该区间章节正在重生中，请稍后再试。当前任务区间: " + regenerationTaskGuardService.getRunningRanges(novel.getId()));
                return;
            }
            try {
                writingProgressService.beginOperation(novel.getId(), NovelWritePhase.REGENERATING_RANGE, from, to);
                for (int chapterNum = from; chapterNum <= to; chapterNum++) {
                    if (generationTaskId != null && generationTaskService.isTaskCancelled(generationTaskId)) {
                        throw new ChapterGenerationAbortedException();
                    }
                    Chapter chapter = gen(novel, chapterNum, novel.getGenerationSetting(), newSetting, generationTaskId);
                    safeSend(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
                }
                safeSend(groupId, "区间重生完成: 第" + from + "章 到 第" + to + "章");
            } finally {
                try {
                    writingProgressService.finishOperation(novel.getId());
                } catch (Exception ex) {
                    log.warn("区间重生工作台收尾失败 novelId={}", novel.getId(), ex);
                }
                regenerationTaskGuardService.releaseRange(novel.getId(), from, to);
            }
        } catch (ChapterGenerationAbortedException e) {
            throw e;
        } catch (Exception e) {
            log.error("regenerate chapter failed", e);
            safeSend(groupId, "Regenerate chapter failed: " + e.getMessage());
        }
    }

    public void repairCharacterProfiles(Long groupId, Integer novelId, boolean forceRegenerate) {
        repairCharacterProfiles(groupId, novelId, CharacterRepairOptions.simple(forceRegenerate));
    }

    public void repairCharacterProfiles(Long groupId, Integer novelId, CharacterRepairOptions options) {
        boolean persistedRepairBench = false;
        Long nid = null;
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) {
                safeSend(groupId, "Novel not found");
                return;
            }
            Novel novel = opt.get();
            nid = novel.getId();
            CharacterRepairOptions effectiveOptions = options == null ? CharacterRepairOptions.simple(false) : options;
            boolean hasUsableProfiles = characterProfileService.hasUsableProfiles(novel.getId());
            if (hasUsableProfiles && !effectiveOptions.forceRegenerate) {
                safeSend(groupId, "角色设定已存在并可用，已执行脏数据修复，无需重建。");
                return;
            }

            writingProgressService.beginOperation(nid, NovelWritePhase.CHARACTER_MAINTENANCE, null, null);
            persistedRepairBench = true;

            WritingPipeline pipeline = resolvePipeline(novel);
            boolean repaired = false;
            String lastError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    String repairSetting = buildCharacterRepairSetting(novel, effectiveOptions);
                    String profileText = generationAgent.generateCharacterProfile(novel.getTopic(), repairSetting, pipeline, false, resolveStyleHints(novel),
                            null, novel.getOutlineGraphJson());
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
        } finally {
            if (persistedRepairBench && nid != null) {
                try {
                    writingProgressService.finishOperation(nid);
                } catch (Exception ex) {
                    log.warn("角色修复工作台收尾失败 novelId={}", nid, ex);
                }
            }
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
        return gen(novel, num, novelSetting, chapterSetting, null);
    }

    private Chapter gen(Novel novel, int num, String novelSetting, String chapterSetting, Long generationTaskId) {
        Long novelPk = novel.getId();
        writingProgressService.onChapterGenerationStart(novelPk, num);
        try {
            return genInner(novel, num, novelSetting, chapterSetting, generationTaskId);
        } finally {
            writingProgressService.onChapterGenerationEnd(novelPk, num);
        }
    }

    private Chapter genInner(Novel novel, int num, String novelSetting, String chapterSetting, Long generationTaskId) {
        String profile = characterProfileService.getStableCharacterProfileBlock(novel.getId());
        List<Chapter> allChapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        WritingPipeline pipeline = resolvePipeline(novel);
        String previousContentFull = num <= 1 ? "" : chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num - 1).map(Chapter::getContent).orElse("");
        String previousContentForDraft = previousContentFull;
        if (pipeline == WritingPipeline.LIGHT_NOVEL && num > 1 && previousContentFull != null && !previousContentFull.isBlank()) {
            previousContentForDraft = chapterFactService.buildLightNovelPreviousChapterBridge(novel.getId(), num - 1, previousContentFull);
        }
        String nextContent = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num + 1).map(Chapter::getContent).orElse("");
        List<CharacterProfile> characterProfiles = characterProfileService.getProfiles(novel.getId());
        List<EntityConsistencyService.LockRule> lockRules = entityConsistencyService.buildStrongLockRules(characterProfiles);
        List<String> lockedNames = lockRules.stream().map(EntityConsistencyService.LockRule::getName).toList();
        String immutableConstraints = entityConsistencyService.buildImmutableConstraints(lockRules);
        String longTermMemory = storyMemoryService.buildStoryMemory(allChapters, lockedNames);
        String factMemory = chapterFactService.buildFactMemory(novel.getId(), 15);
        String snapshotMemory = plotSnapshotService.getLatestSnapshotBlock(novel.getId());
        String narrativeStateForPrompt = buildNarrativeStatePromptBlock(novel.getId());
        WritingStyleHints styleHints = resolveStyleHints(novel);
        NarrativeProfile narrativeProfile = narrativeEngineEnabled
                ? narrativeProfileResolver.resolve(pipeline, novel.getWritingStyleParams())
                : null;
        NarrativePhysicsMode narrativePhysicsMode = narrativeEngineEnabled
                ? narrativeProfileResolver.resolvePhysicsMode(pipeline, novel.getWritingStyleParams())
                : null;
        if (narrativeProfile != null) {
            log.debug("【叙事引擎】novelId={} chapter={} {} physics={}", novel.getId(), num, narrativeProfile.toLogSummary(),
                    narrativePhysicsMode != null ? narrativePhysicsMode.name() : "—");
        }
        String carryoverForPrompt = null;
        if (narrativeM4CarryoverEnabled) {
            carryoverForPrompt = novelRepository.findById(novel.getId())
                    .map(Novel::getNarrativeCarryover)
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .orElse(null);
        }
        NarrativeEngineArtifactSink artifactSink = (narrativeEngineEnabled && narrativeM7ArtifactEnabled)
                ? new NarrativeEngineArtifactSink()
                : null;
        BooleanSupplier abortChapter = generationTaskId == null
                ? null
                : () -> generationTaskService.isTaskCancelled(generationTaskId);
        CharacterNarrativeStateService.ChapterNarrationResolution narration =
                characterNarrativeStateService.resolveForChapter(novel.getId(), num, chapterSetting, novel.getWritingStyleParams());
        String content = generationAgent.generateChapter(
                novel.getDescription(),
                num,
                previousContentForDraft,
                nextContent,
                profile,
                summary(allChapters) + "\n\n" + longTermMemory + "\n\n" + factMemory + "\n\n" + snapshotMemory
                        + narrativeStateForPrompt,
                novelSetting,
                narration.sanitizedChapterSetting(),
                immutableConstraints,
                pipeline,
                novel.isHotMemeEnabled(),
                styleHints,
                narrativeProfile,
                narrativePhysicsMode,
                carryoverForPrompt,
                artifactSink,
                novel.getWritingStyleParams(),
                narration.narrativeInjectBlock(),
                abortChapter
        );
        if (content == null || content.trim().isEmpty()) throw new RuntimeException("AI生成失败：返回内容为空");
        String issueHint = entityConsistencyService.detectNameConsistencyIssue(previousContentFull, content, lockRules);
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
                String secondIssue = entityConsistencyService.detectNameConsistencyIssue(previousContentFull, content, lockRules);
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
                sidecar == null ? null : sidecar.continuityAnchor,
                sidecar == null ? List.of() : sidecar.entities
        );
        // 自动补全“近期多次出现”的新角色设定（扩展层）
        newCharacterIngestService.maybeIngest(novel.getId(), num, lockedNames);
        plotSnapshotService.refreshSnapshotIfNeeded(novel.getId(), num, lockedNames);
        String narrativeLog = narrativeProfile != null ? narrativeProfile.toLogSummary() : null;
        generationLogService.saveGenerationLog(novel.getId(), num, "chapter", len(draft.content), 0, "success", null, narrativeLog);
        String artifactJson = artifactSink != null ? artifactSink.toJsonString(objectMapper, num) : null;
        Chapter saved = saveChapter(novel.getId(), num, draft.title, draft.content, chapterSetting, artifactJson);
        characterNarrativeStateService.maybeApplyStateDelta(novel.getId(), num, saved.getContent(), narration);
        if (narrativeM4CarryoverEnabled) {
            String nextCarry = buildNarrativeCarryoverForNextChapter(sidecar, draft.content, num);
            Long novelId = novel.getId();
            novelRepository.findById(novelId).ifPresent(n -> {
                n.setNarrativeCarryover(nextCarry);
                novelRepository.save(n);
            });
        }
        if (narrativeM9CrosscutEnabled) {
            Long nid = novel.getId();
            novelRepository.findById(nid).ifPresent(fresh -> {
                String json = buildNarrativeStateJson(
                        nid,
                        num,
                        sidecar == null ? null : sidecar.continuityAnchor,
                        sidecar == null ? null : sidecar.entities,
                        sidecar == null ? null : sidecar.facts,
                        fresh.getNarrativeCarryover());
                if (json != null) {
                    fresh.setNarrativeStateJson(json);
                    novelRepository.save(fresh);
                }
            });
        }
        return saved;
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

    /** 更新 Markdown 大纲与可选的冲突图谱 JSON（两阶段大纲）；图谱 null 时清空旧图谱字段。 */
    @Transactional
    public void updateNovelOutline(Long novelId, String description, String outlineGraphJson) {
        Novel n = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("novel not found: " + novelId));
        n.setDescription(description);
        n.setOutlineGraphJson(outlineGraphJson);
        novelRepository.save(n);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic) { return createNovel(groupId, topic, null); }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting) {
        return createNovel(groupId, topic, setting, WritingPipeline.POWER_FANTASY, false, null);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline) {
        return createNovel(groupId, topic, setting, pipeline, false, null);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled) {
        return createNovel(groupId, topic, setting, pipeline, hotMemeEnabled, null, null, null);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                             String writingStyleParamsJson) {
        return createNovel(groupId, topic, setting, pipeline, hotMemeEnabled, writingStyleParamsJson, null, null);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                             String writingStyleParamsJson, String serializationPlatform, String creatorNote) {
        return createNovel(groupId, topic, setting, pipeline, hotMemeEnabled, writingStyleParamsJson, serializationPlatform, creatorNote, null, null);
    }

    @Transactional
    public Novel createNovel(Long groupId, String topic, String setting, WritingPipeline pipeline, boolean hotMemeEnabled,
                             String writingStyleParamsJson, String serializationPlatform, String creatorNote,
                             Integer outlineDetailedPrefixChapters, Integer outlineMinRoadmapChapters) {
        Novel n = new Novel();
        n.setTitle(title(topic));
        n.setTopic(topic);
        n.setGenerationSetting(norm(setting));
        n.setGroupId(groupId);
        n.setDescription("AI generated novel");
        n.setWritingPipeline((pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline).name());
        n.setHotMemeEnabled(hotMemeEnabled);
        n.setWritingStyleParams(normalizeWritingStyleParamsJson(writingStyleParamsJson));
        n.setSerializationPlatform(norm(serializationPlatform));
        n.setCreatorNote(norm(creatorNote));
        n.setOutlineDetailedPrefixChapters(outlineDetailedPrefixChapters);
        n.setOutlineMinRoadmapChapters(outlineMinRoadmapChapters);
        return novelRepository.save(n);
    }

    /** 更新连载平台与创作说明（不参与 AI 提示词，仅展示与项目管理）。 */
    @Transactional
    public void updateBookMeta(Long novelId, String serializationPlatform, String creatorNote) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("未找到该小说"));
        if (serializationPlatform != null) {
            novel.setSerializationPlatform(norm(serializationPlatform));
        }
        if (creatorNote != null) {
            novel.setCreatorNote(norm(creatorNote));
        }
        novelRepository.save(novel);
    }

    @Transactional
    public void updateHotMemeEnabled(Long novelId, boolean enabled) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("novel not found: " + novelId));
        novel.setHotMemeEnabled(enabled);
        novelRepository.save(novel);
    }

    /** 更新全书文风微参 JSON（仅影响后续生成；非空但无效时抛错）。传 null 或空串表示清空。 */
    @Transactional
    public void updateWritingStyleParams(Long novelId, String writingStyleParamsJson) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("novel not found: " + novelId));
        String normalized = normalizeWritingStyleParamsJson(writingStyleParamsJson);
        if (writingStyleParamsJson != null && !writingStyleParamsJson.isBlank() && normalized == null) {
            throw new IllegalArgumentException("writingStyleParams 不是合法 JSON 或不包含支持的字段");
        }
        novel.setWritingStyleParams(normalized);
        novelRepository.save(novel);
    }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content) { return saveChapter(novelId, num, title, content, null); }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content, String setting) {
        return saveChapter(novelId, num, title, content, setting, null);
    }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content, String setting, String narrativeEngineArtifact) {
        Optional<Chapter> existing = chapterRepository.findByNovelIdAndChapterNumber(novelId, num);
        if (existing.isPresent()) {
            Chapter chapter = existing.get();
            chapter.setTitle(title);
            chapter.setContent(content);
            chapter.setGenerationSetting(norm(setting));
            if (narrativeEngineArtifact != null) {
                chapter.setNarrativeEngineArtifact(narrativeEngineArtifact);
            }
            return chapterRepository.save(chapter);
        }
        Chapter c = new Chapter();
        c.setNovelId(novelId);
        c.setChapterNumber(num);
        c.setTitle(title);
        c.setContent(content);
        c.setGenerationSetting(norm(setting));
        if (narrativeEngineArtifact != null) {
            c.setNarrativeEngineArtifact(narrativeEngineArtifact);
        }
        return chapterRepository.save(c);
    }

    /** M7：列出各章叙事引擎侧车 JSON（仅包含有数据的章）。 */
    public List<Map<String, Object>> listNarrativeEngineArtifacts(Long novelId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Chapter c : chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId)) {
            if (c.getNarrativeEngineArtifact() == null || c.getNarrativeEngineArtifact().isBlank()) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("chapterNumber", c.getChapterNumber());
            row.put("chapterTitle", c.getTitle());
            try {
                row.put("artifact", objectMapper.readTree(c.getNarrativeEngineArtifact()));
            } catch (Exception e) {
                row.put("artifactRaw", c.getNarrativeEngineArtifact());
            }
            out.add(row);
        }
        return out;
    }

    /** M7：单章叙事侧车；无数据时 artifact 为 null。 */
    public Map<String, Object> getNarrativeEngineArtifactForChapter(Long novelId, int chapterNumber) {
        Chapter c = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
                .orElseThrow(() -> new NotFoundException("未找到该章节"));
        Map<String, Object> m = new HashMap<>();
        m.put("novelId", novelId);
        m.put("chapterNumber", chapterNumber);
        m.put("chapterTitle", c.getTitle());
        if (c.getNarrativeEngineArtifact() == null || c.getNarrativeEngineArtifact().isBlank()) {
            m.put("artifact", null);
            m.put("message", "本章尚无叙事引擎侧车数据");
            return m;
        }
        try {
            m.put("artifact", objectMapper.readTree(c.getNarrativeEngineArtifact()));
        } catch (Exception e) {
            m.put("artifactRaw", c.getNarrativeEngineArtifact());
        }
        return m;
    }

    /** M9：全书跨章叙事状态快照（只读；数据来自 {@link Novel#getNarrativeStateJson}）。 */
    public Map<String, Object> getNarrativeStateForNovel(Long novelId) {
        Novel n = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        Map<String, Object> m = new HashMap<>();
        m.put("novelId", novelId);
        if (n.getNarrativeStateJson() == null || n.getNarrativeStateJson().isBlank()) {
            m.put("narrativeState", null);
            m.put("message", "尚无跨章叙事状态快照（未开启 M9 或章节生成成功后才会写入）");
            return m;
        }
        try {
            m.put("narrativeState", objectMapper.readTree(n.getNarrativeStateJson()));
        } catch (Exception e) {
            m.put("narrativeStateRaw", n.getNarrativeStateJson());
        }
        return m;
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

    private int persistCharacterProfilesStrict(Long novelId, String topic, String setting, WritingPipeline pipeline, String firstResult,
                                             boolean hotMemeEnabled, WritingStyleHints styleHints,
                                             String outlineMarkdown, String outlineGraphJson) {
        int saved = characterProfileService.saveCharacterProfilesJsonOnly(novelId, firstResult);
        if (saved > 0) {
            return saved;
        }
        Novel fresh = novelRepository.findById(novelId).orElse(null);
        String md = outlineMarkdown != null && !outlineMarkdown.isBlank()
                ? outlineMarkdown : (fresh != null ? fresh.getDescription() : null);
        String gj = outlineGraphJson != null && !outlineGraphJson.isBlank()
                ? outlineGraphJson : (fresh != null ? fresh.getOutlineGraphJson() : null);
        for (int attempt = 1; attempt <= 2; attempt++) {
            String retryPromptSetting = (setting == null ? "" : setting + "\n") + "【输出要求】必须返回严格JSON对象，字段仅包含 characters/name/type/want/fear/knowledge/summary。";
            String retryResult = generationAgent.generateCharacterProfile(topic, retryPromptSetting, pipeline, hotMemeEnabled, styleHints, md, gj);
            saved = characterProfileService.saveCharacterProfilesJsonOnly(novelId, retryResult);
            if (saved > 0) {
                return saved;
            }
        }
        return 0;
    }

    private WritingStyleHints resolveStyleHints(Novel novel) {
        if (novel == null) return null;
        return WritingStyleHints.parseNullable(novel.getWritingStyleParams(), objectMapper);
    }

    /** 校验根 JSON 并整树落库，保留 narrative / cognition / 文笔四层等与 {@link WritingStyleHints} 并存字段。 */
    private String normalizeWritingStyleParamsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.trim());
            if (!root.isObject()) {
                log.warn("writingStyleParams 已忽略（非 JSON 对象）");
                return null;
            }
            if (!WritingStyleParamsSupport.hasSupportedWritingStyleParams(root)) {
                log.warn("writingStyleParams 已忽略（无可识别字段）");
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("writingStyleParams 解析失败: {}", e.getMessage());
            return null;
        }
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

    private static String tailExcerpt(String body, int maxChars) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String t = body.stripTrailing();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(t.length() - maxChars).trim();
    }

    /** M4：写入书本字段，供下一章初稿承接（衔接锚点 + 尾声摘录）。 */
    private String buildNarrativeCarryoverForNextChapter(ChapterSidecar sidecar, String chapterBody, int chapterNum) {
        StringBuilder sb = new StringBuilder();
        sb.append("【上一章收束｜第").append(chapterNum).append("章】\n");
        if (sidecar != null && sidecar.continuityAnchor != null && !sidecar.continuityAnchor.isBlank()) {
            sb.append("衔接锚点：").append(sidecar.continuityAnchor.trim()).append("\n");
        }
        String tail = tailExcerpt(chapterBody, 320);
        if (!tail.isBlank()) {
            sb.append("尾声摘录（勿复述，仅作情绪/场面余波参考）：\n").append(tail);
        }
        String out = sb.toString().trim();
        if (out.length() > 1800) {
            return out.substring(0, 1800) + "…";
        }
        return out;
    }

    private ChapterSidecar extractChapterSidecarSafe(Novel novel, int chapterNumber, String content, WritingPipeline pipeline, List<String> lockedNames) {
        try {
            String json = generationAgent.generateChapterSidecar(content, novel.getDescription(), chapterNumber, pipeline);
            JsonNode root = objectMapper.readTree(extractJsonBody(json));
            ChapterSidecar sidecar = new ChapterSidecar();
            sidecar.title = sanitizeShort(root.path("title").asText(null), 60);
            sidecar.continuityAnchor = sanitizeShort(root.path("continuity_anchor").asText(null), 200);
            sidecar.facts = new ArrayList<>();
            sidecar.entities = new ArrayList<>();
            JsonNode entitiesNode = root.path("entities");
            if (entitiesNode.isArray()) {
                for (JsonNode node : entitiesNode) {
                    String name = sanitizeShort(node.asText(null), 60);
                    if (name != null && !name.isBlank()) sidecar.entities.add(name);
                }
            }
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

    /** M9：上一章落库后的书本快照注入初稿上下文（仅聚合文本，勿照抄）。 */
    private String buildNarrativeStatePromptBlock(Long novelId) {
        if (!narrativeM9CrosscutEnabled) {
            return "";
        }
        String raw = novelRepository.findById(novelId).map(Novel::getNarrativeStateJson).orElse(null);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String clip = clipM9(raw.strip(), 2800);
        return "\n\n【M9 跨章叙事状态快照（衔接用，禁止复述原文）】\n" + clip;
    }

    /** M9：聚合 M4 承接、本章 sidecar、近期 sidecar 事实、最新阶段快照，写入 {@link Novel#setNarrativeStateJson}。 */
    private String buildNarrativeStateJson(Long novelId, int chapterNumber,
                                          String sidecarContinuityAnchor,
                                          List<String> sidecarEntities,
                                          List<String> sidecarFacts,
                                          String carryoverText) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", 1);
            root.put("sourceChapter", chapterNumber);
            root.put("updatedAt", java.time.Instant.now().toString());
            String carry = carryoverText == null ? "" : carryoverText.strip();
            root.put("m4CarryoverPreview", carry.length() > 500 ? carry.substring(0, 500) + "…" : carry);
            if (sidecarContinuityAnchor != null && !sidecarContinuityAnchor.isBlank()) {
                root.put("latestContinuityAnchor", clipM9(sidecarContinuityAnchor.strip(), 400));
            } else {
                root.putNull("latestContinuityAnchor");
            }
            ArrayNode ent = root.putArray("chapterEntities");
            if (sidecarEntities != null) {
                int c = 0;
                for (String e : sidecarEntities) {
                    if (e != null && !e.isBlank() && c++ < 20) {
                        ent.add(e.strip());
                    }
                }
            }
            ArrayNode facts = root.putArray("chapterFactsPreview");
            if (sidecarFacts != null) {
                int c = 0;
                for (String f : sidecarFacts) {
                    if (f != null && !f.isBlank() && c++ < 8) {
                        facts.add(clipM9(f.strip(), 160));
                    }
                }
            }
            ArrayNode recent = root.putArray("recentSidecarFacts");
            List<ChapterFact> all = chapterFactService.getFactsByNovel(novelId);
            int added = 0;
            for (int i = all.size() - 1; i >= 0 && added < 10; i--) {
                ChapterFact f = all.get(i);
                if (!"sidecar_fact".equals(f.getFactType())) {
                    continue;
                }
                if (f.getChapterNumber() == null || f.getChapterNumber() < chapterNumber - 3) {
                    continue;
                }
                ObjectNode o = recent.addObject();
                o.put("chapterNumber", f.getChapterNumber());
                o.put("content", clipM9(f.getFactContent() == null ? "" : f.getFactContent(), 140));
                added++;
            }
            ArrayNode relHints = root.putArray("relationshipHints");
            int rh = 0;
            for (int i = all.size() - 1; i >= 0 && rh < 8; i--) {
                ChapterFact f = all.get(i);
                if (!"character_state".equals(f.getFactType())) {
                    continue;
                }
                if (f.getChapterNumber() == null || f.getChapterNumber() < chapterNumber - 5) {
                    continue;
                }
                String sub = f.getSubjectName() == null ? "" : f.getSubjectName().strip();
                String line = (sub.isEmpty() ? "角色" : sub) + ": " + clipM9(f.getFactContent() == null ? "" : f.getFactContent(), 120);
                relHints.add(line);
                rh++;
            }
            Optional<PlotSnapshot> snap = plotSnapshotService.findLatestSnapshot(novelId);
            if (snap.isPresent()) {
                PlotSnapshot s = snap.get();
                ObjectNode ps = root.putObject("latestPlotSnapshot");
                ps.put("snapshotChapter", s.getSnapshotChapter());
                String body = s.getSnapshotContent() == null ? "" : s.getSnapshotContent().strip();
                ps.put("contentPreview", clipM9(body, 800));
            } else {
                root.putNull("latestPlotSnapshot");
            }
            root.put("tensionRippleHint", buildM9TensionRippleHint(chapterNumber, snap));
            root.put("schemaVersion", 2);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("M9 narrative_state_json 序列化失败 novelId={} ch={}", novelId, chapterNumber, e);
            return null;
        }
    }

    /** M9 深化：据「当前章 vs 最近阶段快照章」给一句余波/收束节奏提示（规则层，非模型）。 */
    private static String buildM9TensionRippleHint(int chapterNumber, Optional<PlotSnapshot> snapOpt) {
        if (snapOpt.isEmpty()) {
            return "尚无阶段快照：衔接依赖章节事实与 M4 承接。";
        }
        Integer snapCh = snapOpt.get().getSnapshotChapter();
        if (snapCh == null || snapCh <= 0) {
            return "阶段快照章号缺失：延续近期事实与承接。";
        }
        int gap = chapterNumber - snapCh;
        if (gap <= 0) {
            return "当前章已落在最新阶段快照窗口内：强衔接上一窗口主线。";
        }
        if (gap <= 3) {
            return "距上次阶段快照较近（" + gap + " 章）：伏笔与情绪余波可适当加重，避免跳档。";
        }
        if (gap <= 10) {
            return "距阶段快照约 " + gap + " 章：平衡旧线回收与新信息推进，防止主线漂移。";
        }
        return "距上次阶段快照已 " + gap + " 章：建议检视是否需收束旧伏笔或补锚点，再开新击。";
    }

    private static String clipM9(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
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
        private List<String> entities;
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