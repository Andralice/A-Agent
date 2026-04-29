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
            for (int i = current + 1; i <= target; i++) {
                continueSingle(groupId, novel, i, chapterSetting);
                Thread.sleep(3000);
            }
            send(groupId, "Auto continue completed");
        } catch (Exception e) {
            log.error("auto continue failed", e);
            safeSend(groupId, "Auto continue failed: " + e.getMessage());
        }
    }

    private void continueSingle(Long groupId, Novel novel, int num, String chapterSetting) {
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        String prev = chapters.isEmpty() ? "" : chapters.get(chapters.size() - 1).getContent();
        Chapter chapter = gen(novel, num, prev, novel.getGenerationSetting(), chapterSetting);
        userStatisticsService.updateGroupStatistics(groupId, 1, len(chapter.getContent()), false);
        send(groupId, novelMessageFormatter.formatNovelMessage(novel, chapter));
    }

    private Chapter gen(Novel novel, int num, String prev, String novelSetting, String chapterSetting) {
        String profile = characterProfileService.getCharacterProfileFromDatabase(novel.getId());
        String summary = summary(chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId()));
        String content = generationAgent.generateChapter(novel.getDescription(), num, prev, profile, summary, novelSetting, chapterSetting);
        generationLogService.saveGenerationLog(novel.getId(), num, "chapter", len(content), 0, "success", null);
        return saveChapter(novel.getId(), num, "Chapter " + num, content, chapterSetting);
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
