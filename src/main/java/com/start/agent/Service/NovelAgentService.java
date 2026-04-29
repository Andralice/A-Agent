package com.start.agent.Service;

import com.start.agent.agent.NovelGenerationAgent;
import com.start.agent.model.Chapter;
import com.start.agent.model.Novel;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class NovelAgentService {
    private final NapCatMessageService messageService;
    private final NovelGenerationAgent generationAgent;
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final GenerationLogService generationLogService;
    private final UserStatisticsService userStatisticsService;
    private final CharacterProfileService characterProfileService;
    private final NovelMessageFormatter novelMessageFormatter;
    private final NovelExportService novelExportService;
    private static final int INIT_CHAPTERS = 5;
    private static final int MAX_AUTO = 20;
    private static final int BATCH_SIZE = 3;
    private static final Random RANDOM = new Random();

    public NovelAgentService(NapCatMessageService messageService, NovelGenerationAgent generationAgent,
                             NovelRepository novelRepository, ChapterRepository chapterRepository,
                             GenerationLogService generationLogService, UserStatisticsService userStatisticsService,
                             CharacterProfileService characterProfileService, NovelMessageFormatter novelMessageFormatter,
                             NovelExportService novelExportService) {
        this.messageService = messageService;
        this.generationAgent = generationAgent;
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.generationLogService = generationLogService;
        this.userStatisticsService = userStatisticsService;
        this.characterProfileService = characterProfileService;
        this.novelMessageFormatter = novelMessageFormatter;
        this.novelExportService = novelExportService;
    }

    public void processAndSend(Long groupId, String topic) { processAndSend(groupId, topic, null); }

    public void processAndSend(Long groupId, String topic, String setting) {
        Long novelId = null;
        try {
            Novel novel = createNovel(groupId, topic, setting);
            novelId = novel.getId();
            String outline = generationAgent.generateOutline(topic, setting);
            generationLogService.saveGenerationLog(novelId, null, "outline", len(outline), 0, "success", null);
            String profile = generationAgent.generateCharacterProfile(topic, setting);
            generationLogService.saveGenerationLog(novelId, null, "character_profile", len(profile), 0, "success", null);
            updateNovelDescription(novelId, outline);
            characterProfileService.saveCharacterProfilesToDatabase(novelId, profile);
            userStatisticsService.updateGroupStatistics(groupId, 0, 0, true);
            send(groupId, "Novel created: " + novel.getTitle());
            String prev = "";
            for (int i = 1; i <= INIT_CHAPTERS; i++) {
                Chapter chapter = gen(novel, i, prev, setting, null);
                prev = chapter.getContent();
                userStatisticsService.updateGroupStatistics(groupId, 1, len(prev), false);
                send(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
            }
        } catch (Exception e) {
            log.error("create novel failed", e);
            generationLogService.saveGenerationLog(novelId, null, "error", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "Novel generation failed: " + e.getMessage());
        }
    }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter) { continueChapter(groupId, novelId, requestedChapter, null); }

    public void continueChapter(Long groupId, Integer novelId, Integer requestedChapter, String chapterSetting) {
        try {
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) return;
            Novel novel = opt.get();
            List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
            int num = requestedChapter == null ? chapters.size() + 1 : requestedChapter;
            Optional<Chapter> old = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num);
            if (old.isPresent()) { send(groupId, novelMessageFormatter.formatExistingChapter(old.get())); return; }
            String prev = chapters.isEmpty() ? "" : chapters.get(chapters.size() - 1).getContent();
            Chapter chapter = gen(novel, num, prev, novel.getGenerationSetting(), chapterSetting);
            send(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
            userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
        } catch (Exception e) {
            log.error("continue failed", e);
            generationLogService.saveGenerationLog(novelId == null ? null : novelId.longValue(), requestedChapter, "chapter", 0, 0, "failed", e.getMessage());
            safeSend(groupId, "Chapter generation failed: " + e.getMessage());
        }
    }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount) { autoContinueChapter(groupId, novelId, targetChapterCount, null); }

    public void autoContinueChapter(Long groupId, Integer novelId, Integer targetChapterCount, String chapterSetting) {
        try {
            int target = targetChapterCount == null ? MAX_AUTO : targetChapterCount;
            if (target > MAX_AUTO) { send(groupId, "auto continue limit is " + MAX_AUTO); return; }
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) return;
            Novel novel = opt.get();
            int current = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId()).size();
            
            int totalChapters = target - current;
            if (totalChapters <= 0) {
                send(groupId, "已达到目标章节数，无需续写");
                return;
            }
            
            send(groupId, String.format("🚀 开始自动续写：第%d章到第%d章（共%d章，分批并行模式）", current + 1, target, totalChapters));
            
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            CompletableFuture.runAsync(() -> {
                for (int batchStart = current + 1; batchStart <= target; batchStart += BATCH_SIZE) {
                    int batchEnd = Math.min(batchStart + BATCH_SIZE - 1, target);
                    List<CompletableFuture<Void>> batch = new ArrayList<>();
                    
                    log.info("【自动续写】开始处理批次：第{}章到第{}章", batchStart, batchEnd);
                    
                    for (int i = batchStart; i <= batchEnd; i++) {
                        final int chapterNum = i;
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            try {
                                continueSingle(groupId, novel, chapterNum, chapterSetting);
                                int success = successCount.incrementAndGet();
                                log.info("【自动续写】✅ 第{}章生成成功 ({}/{})", chapterNum, success + failCount.get(), totalChapters);
                            } catch (Exception e) {
                                int fail = failCount.incrementAndGet();
                                log.error("【自动续写】❌ 第{}章生成失败 ({}/{})", chapterNum, successCount.get() + fail, totalChapters, e);
                                safeSend(groupId, String.format("第%d章生成失败：%s", chapterNum, e.getMessage()));
                            }
                        });
                        batch.add(future);
                    }
                    
                    CompletableFuture.allOf(batch.toArray(new CompletableFuture[0])).join();
                    
                    int completed = successCount.get() + failCount.get();
                    send(groupId, String.format("📊 进度：%d/%d章（成功：%d，失败：%d）", completed, totalChapters, successCount.get(), failCount.get()));
                    
                    if (batchEnd < target) {
                        try {
                            log.info("【自动续写】批次间隔3秒...");
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("【自动续写】被中断");
                            break;
                        }
                    }
                }
                
                send(groupId, String.format("✅ 自动续写完成！总计：%d章，成功：%d章，失败：%d章", totalChapters, successCount.get(), failCount.get()));
                log.info("【自动续写】全部完成 - 成功:{}, 失败:{}", successCount.get(), failCount.get());
            });
            
        } catch (Exception e) {
            log.error("auto continue failed", e);
            safeSend(groupId, "Auto continue failed: " + e.getMessage());
        }
    }

    private void continueSingle(Long groupId, Novel novel, int num, String chapterSetting) {
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        
        String prev = "";
        if (num == 1) {
            prev = "";
        } else {
            Optional<Chapter> prevChapter = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), num - 1);
            if (prevChapter.isPresent()) {
                prev = prevChapter.get().getContent();
            } else {
                prev = chapters.isEmpty() ? "" : chapters.get(chapters.size() - 1).getContent();
            }
        }
        
        log.info("【章节生成】开始生成第{}章，上一章内容长度: {}", num, prev.length());
        
        Chapter chapter = gen(novel, num, prev, novel.getGenerationSetting(), chapterSetting);
        
        if (chapter.getContent() == null || chapter.getContent().trim().isEmpty()) {
            log.error("【章节生成】❌ 第{}章生成内容为空！", num);
            throw new RuntimeException("第" + num + "章生成失败：AI返回内容为空");
        }
        
        log.info("【章节生成】✅ 第{}章生成成功，内容长度: {}", num, chapter.getContent().length());
        
        userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
        send(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
    }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber) { regenerateChapter(groupId, novelId, chapterNumber, null); }

    public void regenerateChapter(Long groupId, Integer novelId, Integer chapterNumber, String newSetting) {
        try {
            if (chapterNumber == null || chapterNumber <= 0) {
                send(groupId, "❌ 章节号必须大于0");
                return;
            }
            
            Optional<Novel> opt = resolve(groupId, novelId);
            if (opt.isEmpty()) {
                send(groupId, "❌ 小说不存在");
                return;
            }
            
            Novel novel = opt.get();
            
            send(groupId, String.format("🔄 开始重新生成第%d章%s...", chapterNumber, newSetting != null ? "（使用新设定）" : ""));
            
            CompletableFuture.runAsync(() -> {
                try {
                    Chapter regeneratedChapter = regenerateSingle(novel, chapterNumber, newSetting);
                    send(groupId, novelMessageFormatter.formatNovelMessage(novel, regeneratedChapter));
                    send(groupId, String.format("✅ 第%d章重新生成完成！", chapterNumber));
                    log.info("【重新生成】✅ 第{}章重新生成成功", chapterNumber);
                } catch (Exception e) {
                    log.error("【重新生成】❌ 第{}章重新生成失败", chapterNumber, e);
                    safeSend(groupId, String.format("❌ 第%d章重新生成失败：%s", chapterNumber, e.getMessage()));
                }
            });
            
        } catch (Exception e) {
            log.error("regenerate chapter failed", e);
            safeSend(groupId, "Regenerate chapter failed: " + e.getMessage());
        }
    }

    private Chapter regenerateSingle(Novel novel, int chapterNumber, String newSetting) {
        log.info("【重新生成】开始重新生成第{}章", chapterNumber);
        
        List<Chapter> allChapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        
        String prev = "";
        String nextSummary = "";
        
        if (chapterNumber == 1) {
            prev = "";
        } else {
            Optional<Chapter> prevChapter = chapterRepository.findByNovelIdAndChapterNumber(novel.getId(), chapterNumber - 1);
            if (prevChapter.isPresent()) {
                prev = prevChapter.get().getContent();
                log.info("【重新生成】已获取第{}章内容作为上文，长度: {}", chapterNumber - 1, prev.length());
            }
        }
        
        if (chapterNumber < allChapters.size()) {
            StringBuilder summary = new StringBuilder();
            for (int i = chapterNumber; i < Math.min(chapterNumber + 2, allChapters.size()); i++) {
                Chapter ch = allChapters.get(i);
                if (ch.getChapterNumber() > chapterNumber) {
                    String content = ch.getContent() != null ? ch.getContent() : "";
                    summary.append("第").append(ch.getChapterNumber()).append("章: ")
                           .append(content.substring(0, Math.min(200, content.length())))
                           .append("\n");
                }
            }
            nextSummary = summary.toString();
            log.info("【重新生成】已获取后续章节摘要，长度: {}", nextSummary.length());
        }
        
        String profile = characterProfileService.getCharacterProfileFromDatabase(novel.getId());
        String setting = newSetting != null ? newSetting : novel.getGenerationSetting();
        
        log.info("【重新生成】调用AI生成第{}章...", chapterNumber);
        String content = generationAgent.generateChapter(novel.getDescription(), chapterNumber, prev, profile, nextSummary, setting, newSetting);
        
        if (content == null || content.trim().isEmpty()) {
            log.error("【重新生成】❌ AI返回内容为空");
            throw new RuntimeException("AI生成失败：返回内容为空");
        }
        
        if (content.contains("待审核内容") || content.contains("未提供具体文本")) {
            log.error("【重新生成】❌ 内容包含审核标记");
            throw new RuntimeException("AI生成失败：内容异常");
        }
        
        TitleExtractionResult result = extractAndRemoveTitle(content);
        String chapterTitle = result.getTitle();
        String cleanContent = result.getContent();
        
        log.info("【重新生成】第{}章标题: {}", chapterNumber, chapterTitle);
        log.info("【内容清理】已删除标题行，原文长度: {} -> 清理后长度: {}", content.length(), cleanContent.length());
        
        generationLogService.saveGenerationLog(novel.getId(), chapterNumber, "regenerate", len(cleanContent), 0, "success", null);
        
        Chapter chapter = saveChapter(novel.getId(), chapterNumber, chapterTitle, cleanContent, setting);
        
        log.info("【重新生成】✅ 第{}章保存成功，内容长度: {}", chapterNumber, cleanContent.length());
        
        userStatisticsService.updateGroupStatistics(novel.getGroupId(), 1, len(cleanContent), false);
        
        return chapter;
    }

    private Chapter gen(Novel novel, int num, String prev, String novelSetting, String chapterSetting) {
        String profile = characterProfileService.getCharacterProfileFromDatabase(novel.getId());
        String summary = summary(chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId()));
        String content = generationAgent.generateChapter(novel.getDescription(), num, prev, profile, summary, novelSetting, chapterSetting);
        
        if (content == null || content.trim().isEmpty()) {
            log.error("【AI生成】❌ 第{}章AI返回内容为空", num);
            throw new RuntimeException("AI生成失败：返回内容为空");
        }
        
        TitleExtractionResult result = extractAndRemoveTitle(content);
        String chapterTitle = result.getTitle();
        String cleanContent = result.getContent();
        
        log.info("【章节标题】第{}章标题: {}", num, chapterTitle);
        log.info("【内容清理】已删除标题行，原文长度: {} -> 清理后长度: {}", content.length(), cleanContent.length());
        
        generationLogService.saveGenerationLog(novel.getId(), num, "chapter", len(cleanContent), 0, "success", null);
        return saveChapter(novel.getId(), num, chapterTitle, cleanContent, chapterSetting);
    }

    private String generateChapterTitle(Novel novel, int chapterNumber, String content) {
        if (content == null || content.trim().isEmpty()) {
            return "第" + chapterNumber + "章";
        }
        
        try {
            TitleExtractionResult result = extractAndRemoveTitle(content);
            return result.getTitle();
        } catch (Exception e) {
            log.warn("【章节标题】生成标题失败，使用默认标题", e);
            return "第" + chapterNumber + "章";
        }
    }

    private TitleExtractionResult extractAndRemoveTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new TitleExtractionResult("未知章节", content);
        }
        
        try {
            String[] lines = content.split("\n");
            
            for (int i = 0; i < Math.min(5, lines.length); i++) {
                String line = lines[i].trim();
                
                if (line.matches("^#\\s*第[\\d一二三四五六七八九十百千零]+章[:：].*$")) {
                    String title = line.replaceAll("^#\\s*", "").trim();
                    String cleanContent = String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, lines.length));
                    return new TitleExtractionResult(title, cleanContent.trim());
                }
                
                if (line.matches("^第[\\d一二三四五六七八九十百千零]+章[:：].*$")) {
                    String title = line.trim();
                    String cleanContent = String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, lines.length));
                    return new TitleExtractionResult(title, cleanContent.trim());
                }
                
                if (line.matches("^Chapter\\s+\\d+[:：].*$")) {
                    String title = line.trim();
                    String cleanContent = String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, lines.length));
                    return new TitleExtractionResult(title, cleanContent.trim());
                }
            }
            
            String firstLine = lines[0].trim();
            if (firstLine.length() > 50) {
                firstLine = firstLine.substring(0, 50);
            }
            
            String title = firstLine.isEmpty() ? "第1章" : firstLine;
            String cleanContent = content;
            
            return new TitleExtractionResult(title, cleanContent);
        } catch (Exception e) {
            log.warn("【标题提取】提取失败，返回原文", e);
            return new TitleExtractionResult("未知章节", content);
        }
    }

    private static class TitleExtractionResult {
        private final String title;
        private final String content;
        
        public TitleExtractionResult(String title, String content) {
            this.title = title;
            this.content = content;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getContent() {
            return content;
        }
    }

    public void listNovels(Long groupId) {
        StringBuilder msg = new StringBuilder("Novel list:\n");
        for (Novel n : novelRepository.findByGroupId(groupId)) msg.append(n.getId()).append(" ").append(n.getTitle()).append("\n");
        send(groupId, msg.toString());
    }

    public void showOutline(Long groupId, Integer novelId) {
        Optional<Novel> n = novelId == null ? latest(groupId) : novelRepository.findById(novelId.longValue());
        send(groupId, n.map(x -> x.getTitle() + "\n" + x.getDescription()).orElse("Novel not found"));
    }

    public void readNovel(Long groupId, Integer novelId, Integer chapterNum) {
        Optional<Novel> n = novelId == null ? latest(groupId) : novelRepository.findById(novelId.longValue());
        if (n.isEmpty()) { send(groupId, "Novel not found"); return; }
        List<Chapter> cs = chapterRepository.findByNovelIdOrderByChapterNumberAsc(n.get().getId());
        Chapter c = chapterNum == null ? (cs.isEmpty() ? null : cs.get(0)) : chapterRepository.findByNovelIdAndChapterNumber(n.get().getId(), chapterNum).orElse(null);
        send(groupId, c == null ? "Chapter not found" : c.getTitle() + "\n" + c.getContent());
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
        Novel n = new Novel();
        n.setTitle(title(topic));
        n.setTopic(topic);
        n.setGenerationSetting(norm(setting));
        n.setGroupId(groupId);
        n.setDescription("AI generated novel");
        return novelRepository.save(n);
    }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content) { return saveChapter(novelId, num, title, content, null); }

    @Transactional
    public Chapter saveChapter(Long novelId, int num, String title, String content, String setting) {
        Optional<Chapter> existing = chapterRepository.findByNovelIdAndChapterNumber(novelId, num);
        if (existing.isPresent()) {
            Chapter chapter = existing.get();
            chapter.setTitle(title);
            chapter.setContent(content);
            chapter.setGenerationSetting(norm(setting));
            return chapterRepository.save(chapter);
        }
        Chapter c = new Chapter();
        c.setNovelId(novelId);
        c.setChapterNumber(num);
        c.setTitle(title);
        c.setContent(content);
        c.setGenerationSetting(norm(setting));
        return chapterRepository.save(c);
    }

    private Optional<Novel> resolve(Long groupId, Integer novelId) { return novelId == null ? latest(groupId) : novelRepository.findById(novelId.longValue()); }
    private Optional<Novel> latest(Long groupId) { return novelRepository.findByGroupId(groupId).stream().findFirst(); }
    private String norm(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
    private int len(String s) { return s == null ? 0 : s.length(); }
    private void send(Long g, String m) { messageService.sendGroupMessage(g, m); }
    private void safeSend(Long g, String m) { try { send(g, m); } catch (Exception e) { log.error("send failed", e); } }
    private String title(String topic) {
        List<String> t = Arrays.asList("%s: %s", "%s legend", "%s rise");
        List<String> s = Arrays.asList("Awakening", "Rebirth", "Fantasy");
        return String.format(t.get(RANDOM.nextInt(t.size())), topic, s.get(RANDOM.nextInt(s.size())));
    }
    private String summary(List<Chapter> cs) {
        if (cs == null || cs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, cs.size() - 3);
        for (int i = from; i < cs.size(); i++) {
            String c = cs.get(i).getContent() == null ? "" : cs.get(i).getContent();
            sb.append(cs.get(i).getTitle()).append(": ").append(c, 0, Math.min(200, c.length())).append("\n");
        }
        return sb.toString();
    }
    public List<Novel> getAllNovels() { return novelRepository.findAll(); }
    public List<Chapter> getChaptersByNovelId(Long novelId) { return chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId); }
    public String exportNovelToTxt(Long novelId) { return novelExportService.exportNovelToTxt(novelId); }
}
