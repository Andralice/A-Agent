package com.start.agent.controller;

import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.ConsistencyAlert;
import com.start.agent.model.GenerationLog;
import com.start.agent.model.Novel;
import com.start.agent.model.WritingPipeline;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.ConsistencyAlertRepository;
import com.start.agent.repository.GenerationLogRepository;
import com.start.agent.repository.NovelRepository;
import com.start.agent.repository.PlotSnapshotRepository;
import com.start.agent.service.NovelAgentService;
import com.start.agent.service.NovelExportService;
import com.start.agent.service.RegenerationTaskGuardService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/novel")
@CrossOrigin(origins = "*")
public class NovelManagementController {
    @Autowired private NovelAgentService agentService;
    @Autowired private NovelRepository novelRepository;
    @Autowired private CharacterProfileRepository characterProfileRepository;
    @Autowired private GenerationLogRepository generationLogRepository;
    @Autowired private ConsistencyAlertRepository consistencyAlertRepository;
    @Autowired private ChapterFactRepository chapterFactRepository;
    @Autowired private PlotSnapshotRepository plotSnapshotRepository;
    @Autowired private NovelExportService novelExportService;
    @Autowired private RegenerationTaskGuardService regenerationTaskGuardService;

    @GetMapping("/list")
    public List<Novel> listNovels() { log.info("【API请求】获取小说列表"); return agentService.getAllNovels(); }

    @GetMapping("/{novelId}/chapters")
    public List<Chapter> getChapters(@PathVariable Long novelId) { log.info("【API请求】获取小说 {} 的章节列表", novelId); return agentService.getChaptersByNovelId(novelId); }

    @GetMapping("/{novelId}")
    public Novel getNovel(@PathVariable Long novelId) { log.info("【API请求】获取小说 {} 的基础信息", novelId); return novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId)); }

    @GetMapping("/{novelId}/outline")
    public Map<String, Object> getOutline(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novel.getId());
        result.put("title", novel.getTitle());
        result.put("topic", novel.getTopic());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("outline", novel.getDescription());
        result.put("ready", novel.getDescription() != null && !novel.getDescription().isBlank() && !"AI generated novel".equals(novel.getDescription()) && !"AI生成的爽文小说".equals(novel.getDescription()));
        result.put("updateTime", novel.getUpdateTime());
        return result;
    }

    @GetMapping("/{novelId}/pipeline")
    public Map<String, Object> getPipeline(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("pipeline", novel.getWritingPipeline() == null ? WritingPipeline.POWER_FANTASY.name() : novel.getWritingPipeline());
        return result;
    }

    @PostMapping("/{novelId}/pipeline")
    public Map<String, Object> updatePipeline(@PathVariable Long novelId, @RequestBody PipelineRequest request) {
        try {
            WritingPipeline pipeline = WritingPipeline.fromPath(request == null ? null : request.getPipeline());
            agentService.updateNovelPipeline(novelId, pipeline);
            Map<String, Object> data = new HashMap<>();
            data.put("pipeline", pipeline.name());
            return success("流水线更新成功（后续章节生效）", data);
        } catch (Exception e) {
            return error("PIPELINE_UPDATE_FAILED", e.getMessage());
        }
    }

    @GetMapping("/{novelId}/characters")
    public List<CharacterProfile> getCharacters(@PathVariable Long novelId) { log.info("【API请求】获取小说 {} 的角色设定", novelId); return characterProfileRepository.findProfilesByNovelIdOrdered(novelId); }

    @PostMapping("/{novelId}/characters/repair")
    public Map<String, Object> repairCharacters(@PathVariable Long novelId, @RequestBody(required = false) RepairCharacterRequest request) {
        try {
            boolean forceRegenerate = request != null && Boolean.TRUE.equals(request.getForceRegenerate());
            String rebuildMode = request == null ? "all" : request.getRebuildMode();
            String characterContextHint = request == null ? null : request.getCharacterContextHint();
            if ((characterContextHint == null || characterContextHint.isBlank()) && request != null) {
                characterContextHint = request.getFirstSwordMaster(); // backward compatibility
            }
            List<String> targetCharacterNames = request == null ? List.of() : request.getTargetCharacterNames();
            String extraHint = request == null ? null : request.getExtraHint();
            NovelAgentService.RebuildMode mode = NovelAgentService.RebuildMode.from(rebuildMode);
            NovelAgentService.CharacterRepairOptions options =
                    new NovelAgentService.CharacterRepairOptions(forceRegenerate, mode, characterContextHint, targetCharacterNames, extraHint);
            CompletableFuture.runAsync(() -> agentService.repairCharacterProfiles(0L, novelId.intValue(), options));
            Map<String, Object> data = new HashMap<>();
            data.put("forceRegenerate", forceRegenerate);
            data.put("rebuildMode", mode.name().toLowerCase());
            return success("角色设定修复任务已启动，请稍后刷新角色列表", data);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) return error("INVALID_ARGUMENT", e.getMessage());
            return error("CHARACTER_REPAIR_TASK_FAILED", e.getMessage());
        }
    }

    @GetMapping("/{novelId}/generation-logs")
    public List<GenerationLog> getGenerationLogs(@PathVariable Long novelId) { log.info("【API请求】获取小说 {} 的生成日志", novelId); return generationLogRepository.findByNovelId(novelId); }

    @GetMapping("/{novelId}/consistency-alerts")
    public List<ConsistencyAlert> getConsistencyAlerts(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的一致性告警", novelId);
        return consistencyAlertRepository.findByNovelIdOrderByCreateTimeDesc(novelId);
    }

    @GetMapping("/{novelId}/chapter-facts")
    public List<ChapterFact> getChapterFacts(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的章节事实", novelId);
        return chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId);
    }

    @GetMapping("/{novelId}/chapter-sidecar")
    public List<Map<String, Object>> getChapterSidecar(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的章节 sidecar 视图", novelId);
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        Map<Integer, Chapter> chapterMap = chapters.stream().collect(Collectors.toMap(Chapter::getChapterNumber, c -> c, (a, b) -> a));
        Map<Integer, List<ChapterFact>> grouped = chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId).stream()
                .filter(f -> "sidecar_fact".equals(f.getFactType()) || "continuity_anchor".equals(f.getFactType()))
                .collect(Collectors.groupingBy(ChapterFact::getChapterNumber, java.util.LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<ChapterFact>> entry : grouped.entrySet()) {
            Integer chapterNumber = entry.getKey();
            List<ChapterFact> facts = entry.getValue();
            Chapter chapter = chapterMap.get(chapterNumber);
            Map<String, Object> item = new HashMap<>();
            item.put("chapterNumber", chapterNumber);
            item.put("chapterTitle", chapter == null ? ("第" + chapterNumber + "章") : chapter.getTitle());
            item.put("facts", facts.stream().filter(f -> "sidecar_fact".equals(f.getFactType())).map(ChapterFact::getFactContent).toList());
            item.put("continuityAnchor", facts.stream().filter(f -> "continuity_anchor".equals(f.getFactType())).map(ChapterFact::getFactContent).findFirst().orElse(null));
            result.add(item);
        }
        return result;
    }

    @GetMapping("/{novelId}/plot-snapshots")
    public Object getPlotSnapshots(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的阶段快照", novelId);
        return plotSnapshotRepository.findByNovelIdOrderBySnapshotChapterDesc(novelId);
    }

    @GetMapping("/{novelId}/regeneration-tasks")
    public Map<String, Object> getRegenerationTasks(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的区间重生任务状态", novelId);
        List<String> runningRanges = regenerationTaskGuardService.getRunningRanges(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("hasRunningTask", !runningRanges.isEmpty());
        result.put("runningRanges", runningRanges);
        return result;
    }

    @GetMapping("/{novelId}/progress")
    public Map<String, Object> getProgress(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> logs = generationLogRepository.findByNovelId(novelId);
        boolean outlineReady = novel.getDescription() != null && !novel.getDescription().isBlank() && !"AI generated novel".equals(novel.getDescription()) && !"AI生成的爽文小说".equals(novel.getDescription());
        long failedCount = logs.stream().filter(log -> "failed".equals(log.getStatus())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("title", novel.getTitle());
        result.put("outlineReady", outlineReady);
        result.put("charactersReady", !profiles.isEmpty());
        result.put("chapterCount", chapters.size());
        result.put("generationLogCount", logs.size());
        result.put("failedCount", failedCount);
        result.put("status", "success");
        result.put("code", "OK");
        result.put("generationStatus", failedCount > 0 ? "failed" : (outlineReady && !profiles.isEmpty() ? "generating_or_ready" : "generating"));
        result.put("lastUpdateTime", novel.getUpdateTime());
        return result;
    }

    @GetMapping("/{novelId}/creative-process")
    public Map<String, Object> getCreativeProcess(@PathVariable Long novelId) {
        log.info("【API请求】获取小说 {} 的创作过程详情", novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> logs = generationLogRepository.findByNovelId(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("novel", novel);
        result.put("outline", novel.getDescription());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("characterProfiles", profiles);
        result.put("chapters", chapters);
        result.put("generationLogs", logs);
        result.put("timeline", buildCreativeTimeline(novel, profiles, chapters, logs));
        return result;
    }

    private List<Map<String, Object>> buildCreativeTimeline(Novel novel, List<CharacterProfile> profiles, List<Chapter> chapters, List<GenerationLog> logs) {
        List<Map<String, Object>> timeline = new java.util.ArrayList<>();
        Map<String, Object> novelStep = new HashMap<>();
        novelStep.put("type", "novel_created"); novelStep.put("title", "创建小说"); novelStep.put("content", novel.getTopic()); novelStep.put("setting", novel.getGenerationSetting()); novelStep.put("createTime", novel.getCreateTime()); timeline.add(novelStep);
        Map<String, Object> outlineStep = new HashMap<>();
        outlineStep.put("type", "outline"); outlineStep.put("title", "生成故事大纲"); outlineStep.put("content", novel.getDescription()); outlineStep.put("logs", logs.stream().filter(log -> "outline".equals(log.getGenerationType())).toList()); timeline.add(outlineStep);
        Map<String, Object> profileStep = new HashMap<>();
        profileStep.put("type", "character_profile"); profileStep.put("title", "生成角色设定"); profileStep.put("content", profiles); profileStep.put("logs", logs.stream().filter(log -> "character_profile".equals(log.getGenerationType())).toList()); timeline.add(profileStep);
        for (Chapter chapter : chapters) {
            Map<String, Object> step = new HashMap<>();
            step.put("type", "chapter"); step.put("title", chapter.getTitle()); step.put("chapterNumber", chapter.getChapterNumber()); step.put("content", chapter.getContent()); step.put("setting", chapter.getGenerationSetting()); step.put("createTime", chapter.getCreateTime());
            step.put("logs", logs.stream().filter(log -> "chapter".equals(log.getGenerationType()) && chapter.getChapterNumber().equals(log.getChapterNumber())).toList());
            timeline.add(step);
        }
        return timeline;
    }

    @PostMapping("/create")
    public Map<String, Object> createNovel(@RequestBody(required = false) CreateRequest request) {
        log.info("【API请求】新建小说，题材: {}, 设定长度: {}", request == null ? "未提供" : request.getTopic(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) return error("INVALID_ARGUMENT", "题材不能为空");
            CompletableFuture.runAsync(() -> agentService.processAndSend(0L, request.getTopic(), request.getGenerationSetting()));
            return success("创作任务已启动，请通过进度接口轮询查看", null);
        } catch (Exception e) { return error("CREATE_TASK_FAILED", e.getMessage()); }
    }

    @PostMapping("/{novelId}/continue")
    public Map<String, Object> continueNovel(@PathVariable Long novelId, @RequestBody(required = false) ContinueRequest request) {
        log.info("【API请求】续写小说 {}, 目标章节: {}, 设定长度: {}", novelId, request == null ? "自动下一章" : request.getChapterNumber(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
            Integer chapterNumber = request == null ? null : request.getChapterNumber();
            int targetChapter = chapterNumber == null ? chapters.size() + 1 : chapterNumber;
            if (regenerationTaskGuardService.hasOverlap(novelId, targetChapter, targetChapter)) {
                return error("TASK_CONFLICT", "目标章节正在重生/续写中，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.continueChapter(0L, novelId.intValue(), chapterNumber, generationSetting));
            return success("续写任务已启动，请通过进度接口轮询查看", null);
        } catch (Exception e) { return error("CONTINUE_TASK_FAILED", e.getMessage()); }
    }

    @PostMapping("/{novelId}/auto-continue")
    public Map<String, Object> autoContinueNovel(@PathVariable Long novelId, @RequestBody(required = false) AutoContinueRequest request) {
        log.info("【API请求】自动续写小说 {}, 目标章节数: {}, 设定长度: {}", novelId, request == null ? "默认" : request.getTargetChapterCount(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
            int current = chapters.size();
            Integer targetChapterCount = request == null ? null : request.getTargetChapterCount();
            int target = targetChapterCount == null ? current + 1 : targetChapterCount;
            if (target > current && regenerationTaskGuardService.hasOverlap(novelId, current + 1, target)) {
                return error("TASK_CONFLICT", "目标区间存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.autoContinueChapter(0L, novelId.intValue(), targetChapterCount, generationSetting));
            return success("自动续写任务已启动，请通过进度接口轮询查看", null);
        } catch (Exception e) { return error("AUTO_CONTINUE_TASK_FAILED", e.getMessage()); }
    }

    @PostMapping("/{novelId}/chapters/{chapterNumber}/regenerate")
    public Map<String, Object> regenerateChapter(@PathVariable Long novelId, @PathVariable Integer chapterNumber, @RequestBody(required = false) RegenerateRequest request) {
        log.info("【API请求】重新生成小说 {} 的第{}章，设定长度: {}", novelId, chapterNumber, textLength(request == null ? null : request.getGenerationSetting()));
        try {
            if (chapterNumber <= 0) { return error("INVALID_ARGUMENT", "章节号必须大于0"); }
            if (regenerationTaskGuardService.hasOverlap(novelId, chapterNumber, chapterNumber)) {
                return error("TASK_CONFLICT", "该章节已存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.regenerateChapter(0L, novelId.intValue(), chapterNumber, generationSetting));
            return success("重新生成任务已启动，请通过进度接口轮询查看", null);
        } catch (Exception e) { return error("REGENERATE_TASK_FAILED", e.getMessage()); }
    }

    @PostMapping("/{novelId}/chapters/regenerate-range")
    public Map<String, Object> regenerateRange(@PathVariable Long novelId, @RequestBody RegenerateRangeRequest request) {
        try {
            if (request == null || request.getStartChapter() == null || request.getEndChapter() == null) {
                return error("INVALID_ARGUMENT", "startChapter 和 endChapter 不能为空");
            }
            Integer startChapter = request.getStartChapter();
            Integer endChapter = request.getEndChapter();
            int from = Math.min(startChapter, endChapter);
            int to = Math.max(startChapter, endChapter);
            if (regenerationTaskGuardService.hasOverlap(novelId, from, to)) {
                return error("TASK_CONFLICT", "目标区间存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.regenerateChapterRange(0L, novelId.intValue(), startChapter, endChapter, generationSetting));
            Map<String, Object> data = new HashMap<>();
            data.put("startChapter", startChapter);
            data.put("endChapter", endChapter);
            return success("区间重生任务已启动，请通过进度接口轮询查看", data);
        } catch (Exception e) {
            return error("REGENERATE_RANGE_TASK_FAILED", e.getMessage());
        }
    }

    @GetMapping("/{novelId}/export")
    public String exportNovel(@PathVariable Long novelId) { log.info("【API请求】导出小说 {}", novelId); return agentService.exportNovelToTxt(novelId); }

    @GetMapping("/{novelId}/export-health")
    public Map<String, Object> checkExportHealth(@PathVariable Long novelId) {
        log.info("【API请求】导出前体检 {}", novelId);
        return novelExportService.checkExportHealth(novelId);
    }
    private int textLength(String text) { return text == null ? 0 : text.length(); }
    private Map<String, Object> success(String message, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("message", message);
        if (data != null) result.putAll(data);
        return result;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    @Data static class CreateRequest { private String topic; private String generationSetting; }
    @Data static class ContinueRequest { private Integer chapterNumber; private String generationSetting; }
    @Data static class AutoContinueRequest { private Integer targetChapterCount; private String generationSetting; }
    @Data static class RegenerateRequest { private String generationSetting; }
    @Data static class RegenerateRangeRequest { private Integer startChapter; private Integer endChapter; private String generationSetting; }
    @Data static class PipelineRequest { private String pipeline; }
    @Data static class RepairCharacterRequest {
        private Boolean forceRegenerate;
        private String rebuildMode;
        private String characterContextHint;
        // backward compatibility
        private String firstSwordMaster;
        private List<String> targetCharacterNames;
        private String extraHint;
    }
}
