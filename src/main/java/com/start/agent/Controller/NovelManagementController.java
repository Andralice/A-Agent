package com.start.agent.Controller;

import com.start.agent.Service.NovelAgentService;
import com.start.agent.model.Chapter;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.GenerationLog;
import com.start.agent.model.Novel;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.GenerationLogRepository;
import com.start.agent.repository.NovelRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/novel")
@CrossOrigin(origins = "*")
public class NovelManagementController {

    @Autowired
    private NovelAgentService agentService;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private GenerationLogRepository generationLogRepository;

    @GetMapping("/list")
    public List<Novel> listNovels() {
        log.info("【API请求】获取小说列表");
        return agentService.getAllNovels();
    }

    @GetMapping("/{novelId}/chapters")
    public List<Chapter> getChapters(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的章节列表", novelId);
        return agentService.getChaptersByNovelId(novelId);
    }

    @GetMapping("/{novelId}")
    public Novel getNovel(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的基础信息", novelId);
        return novelRepository.findById(novelId)
                .orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
    }

    @GetMapping("/{novelId}/outline")
    public Map<String, Object> getOutline(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novel.getId());
        result.put("title", novel.getTitle());
        result.put("topic", novel.getTopic());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("outline", novel.getDescription());
        result.put("ready", novel.getDescription() != null && !novel.getDescription().isBlank()
                && !"AI generated novel".equals(novel.getDescription())
                && !"AI生成的爽文小说".equals(novel.getDescription()));
        result.put("updateTime", novel.getUpdateTime());
        return result;
    }

    @GetMapping("/{novelId}/characters")
    public List<CharacterProfile> getCharacters(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的角色设定", novelId);
        return characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
    }

    @GetMapping("/{novelId}/generation-logs")
    public List<GenerationLog> getGenerationLogs(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的生成日志", novelId);
        return generationLogRepository.findByNovelId(novelId);
    }

    @GetMapping("/{novelId}/progress")
    public Map<String, Object> getProgress(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> characterProfiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> generationLogs = generationLogRepository.findByNovelId(novelId);

        boolean outlineReady = novel.getDescription() != null && !novel.getDescription().isBlank()
                && !"AI generated novel".equals(novel.getDescription())
                && !"AI生成的爽文小说".equals(novel.getDescription());
        boolean charactersReady = !characterProfiles.isEmpty();
        long failedCount = generationLogs.stream().filter(log -> "failed".equals(log.getStatus())).count();
        boolean hasRecentFailure = failedCount > 0;

        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("title", novel.getTitle());
        result.put("outlineReady", outlineReady);
        result.put("charactersReady", charactersReady);
        result.put("chapterCount", chapters.size());
        result.put("generationLogCount", generationLogs.size());
        result.put("failedCount", failedCount);
        result.put("status", hasRecentFailure ? "failed" : (outlineReady && charactersReady ? "generating_or_ready" : "generating"));
        result.put("lastUpdateTime", novel.getUpdateTime());
        return result;
    }

    @GetMapping("/{novelId}/creative-process")
    public Map<String, Object> getCreativeProcess(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的创作过程详情", novelId);
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> characterProfiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> generationLogs = generationLogRepository.findByNovelId(novelId);

        Map<String, Object> result = new HashMap<>();
        result.put("novel", novel);
        result.put("outline", novel.getDescription());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("characterProfiles", characterProfiles);
        result.put("chapters", chapters);
        result.put("generationLogs", generationLogs);
        result.put("timeline", buildCreativeTimeline(novel, characterProfiles, chapters, generationLogs));
        return result;
    }

    private List<Map<String, Object>> buildCreativeTimeline(Novel novel,
                                                            List<CharacterProfile> characterProfiles,
                                                            List<Chapter> chapters,
                                                            List<GenerationLog> generationLogs) {
        List<Map<String, Object>> timeline = new java.util.ArrayList<>();
        Map<String, Object> novelStep = new HashMap<>();
        novelStep.put("type", "novel_created");
        novelStep.put("title", "创建小说");
        novelStep.put("content", novel.getTopic());
        novelStep.put("setting", novel.getGenerationSetting());
        novelStep.put("createTime", novel.getCreateTime());
        timeline.add(novelStep);

        Map<String, Object> outlineStep = new HashMap<>();
        outlineStep.put("type", "outline");
        outlineStep.put("title", "生成故事大纲");
        outlineStep.put("content", novel.getDescription());
        outlineStep.put("logs", generationLogs.stream().filter(log -> "outline".equals(log.getGenerationType())).toList());
        timeline.add(outlineStep);

        Map<String, Object> profileStep = new HashMap<>();
        profileStep.put("type", "character_profile");
        profileStep.put("title", "生成角色设定");
        profileStep.put("content", characterProfiles);
        profileStep.put("logs", generationLogs.stream().filter(log -> "character_profile".equals(log.getGenerationType())).toList());
        timeline.add(profileStep);

        for (Chapter chapter : chapters) {
            Map<String, Object> chapterStep = new HashMap<>();
            chapterStep.put("type", "chapter");
            chapterStep.put("title", chapter.getTitle());
            chapterStep.put("chapterNumber", chapter.getChapterNumber());
            chapterStep.put("content", chapter.getContent());
            chapterStep.put("setting", chapter.getGenerationSetting());
            chapterStep.put("createTime", chapter.getCreateTime());
            chapterStep.put("logs", generationLogs.stream()
                    .filter(log -> "chapter".equals(log.getGenerationType())
                            && chapter.getChapterNumber().equals(log.getChapterNumber()))
                    .toList());
            timeline.add(chapterStep);
        }
        return timeline;
    }

    @PostMapping("/create")
    public Map<String, Object> createNovel(@RequestBody(required = false) CreateRequest request) {
        log.info("【API请求】新建小说，题材: {}, 设定长度: {}", request == null ? "未提供" : request.getTopic(), textLength(request == null ? null : request.getGenerationSetting()));
        Map<String, Object> result = new HashMap<>();
        try {
            if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) {
                throw new IllegalArgumentException("题材不能为空");
            }
            CompletableFuture.runAsync(() -> agentService.processAndSend(0L, request.getTopic(), request.getGenerationSetting()));
            result.put("status", "success");
            result.put("message", "创作任务已启动，请通过进度接口轮询查看");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/{novelId}/continue")
    public Map<String, Object> continueNovel(@PathVariable Long novelId, @RequestBody(required = false) ContinueRequest request) {
        log.info("【API请求】续写小说 {}, 目标章节: {}, 设定长度: {}", novelId, request == null ? "自动下一章" : request.getChapterNumber(), textLength(request == null ? null : request.getGenerationSetting()));
        Map<String, Object> result = new HashMap<>();
        try {
            Integer chapterNumber = request == null ? null : request.getChapterNumber();
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.continueChapter(0L, novelId.intValue(), chapterNumber, generationSetting));
            result.put("status", "success");
            result.put("message", "续写任务已启动，请通过进度接口轮询查看");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/{novelId}/auto-continue")
    public Map<String, Object> autoContinueNovel(@PathVariable Long novelId, @RequestBody(required = false) AutoContinueRequest request) {
        log.info("【API请求】自动续写小说 {}, 目标章节数: {}, 设定长度: {}", novelId, request == null ? "默认" : request.getTargetChapterCount(), textLength(request == null ? null : request.getGenerationSetting()));
        Map<String, Object> result = new HashMap<>();
        try {
            int targetChapterCount = request == null || request.getTargetChapterCount() == null ? 20 : request.getTargetChapterCount();
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.autoContinueChapter(0L, novelId.intValue(), targetChapterCount, generationSetting));
            result.put("status", "success");
            result.put("message", "自动续写任务已启动，请通过进度接口轮询查看");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/{novelId}/chapters/{chapterNumber}/regenerate")
    public Map<String, Object> regenerateChapter(@PathVariable Long novelId, @PathVariable Integer chapterNumber, @RequestBody(required = false) RegenerateRequest request) {
        log.info("【API请求】重新生成小说 {} 的第{}章，设定长度: {}", novelId, chapterNumber, textLength(request == null ? null : request.getGenerationSetting()));
        Map<String, Object> result = new HashMap<>();
        try {
            if (chapterNumber <= 0) {
                result.put("status", "error");
                result.put("message", "章节号必须大于0");
                return result;
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.regenerateChapter(0L, novelId.intValue(), chapterNumber, generationSetting));
            result.put("status", "success");
            result.put("message", "重新生成任务已启动，请通过进度接口轮询查看");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/{novelId}/export")
    public String exportNovel(@PathVariable Long novelId) {
        log.info("【API请求】导出小说 {}", novelId);
        return agentService.exportNovelToTxt(novelId);
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    @Data
    static class CreateRequest {
        private String topic;
        private String generationSetting;
    }

    @Data
    static class ContinueRequest {
        private Integer chapterNumber;
        private String generationSetting;
    }

    @Data
    static class AutoContinueRequest {
        private Integer targetChapterCount;
        private String generationSetting;
    }

    @Data
    static class RegenerateRequest {
        private String generationSetting;
    }
}

