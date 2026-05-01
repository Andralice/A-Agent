package com.start.agent.controller;

import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.ChapterWriteState;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.ConsistencyAlert;
import com.start.agent.model.GenerationLog;
import com.start.agent.model.GenerationTask;
import com.start.agent.model.Novel;
import com.start.agent.model.NovelWritePhase;
import com.start.agent.model.WritingPipeline;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.ConsistencyAlertRepository;
import com.start.agent.repository.GenerationLogRepository;
import com.start.agent.repository.NovelRepository;
import com.start.agent.repository.PlotSnapshotRepository;
import com.start.agent.service.NovelAgentService;
import com.start.agent.service.NovelExportService;
import com.start.agent.service.GenerationTaskService;
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
    private static final List<String> ACTIVE_TASK_STATUSES = List.of("PENDING", "RUNNING");
    @Autowired private NovelAgentService agentService;
    @Autowired private NovelRepository novelRepository;
    @Autowired private CharacterProfileRepository characterProfileRepository;
    @Autowired private GenerationLogRepository generationLogRepository;
    @Autowired private ConsistencyAlertRepository consistencyAlertRepository;
    @Autowired private ChapterFactRepository chapterFactRepository;
    @Autowired private PlotSnapshotRepository plotSnapshotRepository;
    @Autowired private NovelExportService novelExportService;
    @Autowired private RegenerationTaskGuardService regenerationTaskGuardService;
    @Autowired private GenerationTaskService generationTaskService;

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
        // 任务化后：优先展示 DB 中的活跃任务区间（跨实例）
        try {
            List<GenerationTask> active = generationTaskService.listTasksByNovel(novelId).stream()
                    .filter(t -> ACTIVE_TASK_STATUSES.contains(t.getStatus()))
                    .filter(t -> t.getRangeFrom() != null && t.getRangeTo() != null)
                    .toList();
            if (!active.isEmpty()) {
                runningRanges = active.stream().map(t -> t.getRangeFrom() + "-" + t.getRangeTo()).distinct().toList();
            }
        } catch (Exception ignore) {
        }
        Novel novel = novelRepository.findById(novelId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("hasRunningTask", !runningRanges.isEmpty());
        result.put("runningRanges", runningRanges);
        if (novel != null) {
            result.put("persistedWorkbench", buildPersistedWorkbench(novel));
        }
        return result;
    }

    @GetMapping("/{novelId}/generation-tasks")
    public Map<String, Object> getGenerationTasks(@PathVariable Long novelId) {
        List<GenerationTask> tasks = generationTaskService.listTasksByNovel(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("novelId", novelId);
        result.put("tasks", tasks);
        return result;
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getGenerationTask(@PathVariable Long taskId) {
        return generationTaskService.getTask(taskId)
                .map(task -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("code", "OK");
                    result.put("task", task);
                    return result;
                })
                .orElseGet(() -> error("TASK_NOT_FOUND", "任务不存在: " + taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelGenerationTask(@PathVariable Long taskId) {
        boolean ok = generationTaskService.cancelTask(taskId);
        if (!ok) return error("TASK_CANCEL_FAILED", "任务不存在或已完成，无法取消");
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return success("任务已取消", data);
    }

    @PostMapping("/tasks/{taskId}/kick")
    public Map<String, Object> kickPendingTask(@PathVariable Long taskId) {
        boolean ok = generationTaskService.kickIfPending(taskId);
        if (!ok) return error("TASK_KICK_FAILED", "仅 PENDING 任务可手动触发执行，或任务不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return success("任务已触发执行", data);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Map<String, Object> retryGenerationTask(@PathVariable Long taskId) {
        return generationTaskService.retryTask(taskId)
                .map(task -> {
                    generationTaskService.executeAsync(task.getId());
                    Map<String, Object> data = new HashMap<>();
                    data.put("taskId", task.getId());
                    data.put("status", task.getStatus());
                    return success("任务已重新入队并恢复执行", data);
                })
                .orElseGet(() -> error("TASK_RETRY_FAILED", "仅 FAILED/CANCELLED 任务可重试，或任务不存在"));
    }

    /** 前端监控：聚合 DB 工作台 + 内存区间锁 + 非 READY 章节列表。 */
    @GetMapping("/{novelId}/writing-monitor")
    public Map<String, Object> getWritingMonitor(@PathVariable Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在，ID: " + novelId));
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("novelId", novelId);
        result.putAll(buildPersistedWorkbench(novel));
        result.put("volatileGuardRanges", regenerationTaskGuardService.getRunningRanges(novelId));
        result.put("hasVolatileGuardOverlap", regenerationTaskGuardService.hasRunningTask(novelId));

        List<Map<String, Object>> chapterBusyViews = new java.util.ArrayList<>();
        for (Chapter c : agentService.getChaptersByNovelId(novelId)) {
            String st = c.getWriteState() == null ? ChapterWriteState.READY.name() : c.getWriteState();
            if (!ChapterWriteState.READY.name().equals(st)) {
                Map<String, Object> row = new HashMap<>();
                row.put("chapterNumber", c.getChapterNumber());
                row.put("title", c.getTitle());
                row.put("writeState", st);
                row.put("writeStateUpdatedAt", c.getWriteStateUpdatedAt());
                chapterBusyViews.add(row);
            }
        }
        result.put("chaptersWithActiveWriteMarks", chapterBusyViews);
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
        result.putAll(buildPersistedWorkbench(novel));
        return result;
    }

    /** 可与 {@link NovelWritePhase} / {@link ChapterWriteState} 配套展示的核心字段快照。 */
    private Map<String, Object> buildPersistedWorkbench(Novel novel) {
        Map<String, Object> map = new HashMap<>();
        if (novel == null) return map;
        // 1) 先取 DB 任务（最可信，可跨实例），用于展示“全区间 + 当前进度”
        try {
            List<GenerationTask> active = generationTaskService.listTasksByNovel(novel.getId()).stream()
                    .filter(t -> ACTIVE_TASK_STATUSES.contains(t.getStatus()))
                    .filter(t -> t.getRangeFrom() != null && t.getRangeTo() != null)
                    .toList();
            if (!active.isEmpty()) {
                // 优先 RUNNING，其次 PENDING
                GenerationTask picked = active.stream().filter(t -> "RUNNING".equals(t.getStatus())).findFirst().orElse(active.get(0));
                int from = Math.min(picked.getRangeFrom(), picked.getRangeTo());
                int to = Math.max(picked.getRangeFrom(), picked.getRangeTo());
                Integer cur = picked.getCurrentChapter();
                int cursor = cur == null ? from : Math.min(to, Math.max(from, cur + 1));
                map.put("writePhase", mapTaskTypeToPhase(picked.getTaskType()));
                map.put("writeRangeFrom", from);
                map.put("writeRangeTo", to);
                map.put("writeCursorChapter", cursor);
                map.put("writePhaseDetail", picked.getTaskType() + " " + from + "-" + to);
                map.put("writeStartedAt", picked.getStartedAt());
                map.put("writeUpdatedAt", picked.getHeartbeatAt());
                map.put("isBusyPersisted", true);
                map.put("activeTaskId", picked.getId());
                map.put("activeTaskStatus", picked.getStatus());
                map.put("activeTaskCurrentChapter", picked.getCurrentChapter());
                return map;
            }
        } catch (Exception ignore) {
        }

        // 2) 兜底：旧版工作台字段（单章流程仍会写）
        String phase = novel.getWritePhase() == null || novel.getWritePhase().isBlank()
                ? NovelWritePhase.IDLE.name() : novel.getWritePhase();
        boolean busyPersisted = !NovelWritePhase.IDLE.name().equals(phase);
        map.put("writePhase", phase);
        map.put("writeRangeFrom", novel.getWriteRangeFrom());
        map.put("writeRangeTo", novel.getWriteRangeTo());
        map.put("writeCursorChapter", novel.getWriteCursorChapter());
        map.put("writePhaseDetail", novel.getWritePhaseDetail());
        map.put("writeStartedAt", novel.getWriteStartedAt());
        map.put("writeUpdatedAt", novel.getWriteUpdatedAt());
        map.put("isBusyPersisted", busyPersisted);
        return map;
    }

    private String mapTaskTypeToPhase(String taskType) {
        if (taskType == null) return NovelWritePhase.IDLE.name();
        return switch (taskType) {
            case "REGENERATE_RANGE" -> NovelWritePhase.REGENERATING_RANGE.name();
            case "AUTO_CONTINUE_RANGE" -> NovelWritePhase.AUTO_CONTINUE_RANGE.name();
            case "CONTINUE_SINGLE" -> NovelWritePhase.SINGLE_CONTINUE.name();
            default -> NovelWritePhase.IDLE.name();
        };
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
            // 先落库小说记录，再用可恢复任务驱动首次生成（避免进程中断丢任务）
            Novel novel = agentService.createNovel(0L, request.getTopic(), request.getGenerationSetting());
            GenerationTask task = generationTaskService.enqueueInitialBootstrapTask(novel.getId(), 5);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novel.getId());
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            return success("创作任务已入队并启动（可恢复），请通过任务接口轮询查看", data);
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
            GenerationTask task = generationTaskService.enqueueContinueTask(novelId, chapterNumber, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("targetChapter", task.getRangeFrom());
            return success("续写任务已入队并启动，可通过 generation-tasks 查看恢复进度", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", e.getMessage());
            return error("CONTINUE_TASK_FAILED", e.getMessage());
        }
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
            GenerationTask task = generationTaskService.enqueueAutoContinueTask(novelId, targetChapterCount, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("rangeFrom", task.getRangeFrom());
            data.put("rangeTo", task.getRangeTo());
            return success("自动续写任务已入队并启动，可在重启后继续", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", e.getMessage());
            return error("AUTO_CONTINUE_TASK_FAILED", e.getMessage());
        }
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
            GenerationTask task = generationTaskService.enqueueRegenerateRangeTask(novelId, chapterNumber, chapterNumber, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("targetChapter", chapterNumber);
            return success("重新生成任务已入队并启动，可在重启后恢复", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", e.getMessage());
            return error("REGENERATE_TASK_FAILED", e.getMessage());
        }
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
            GenerationTask task = generationTaskService.enqueueRegenerateRangeTask(novelId, startChapter, endChapter, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("startChapter", startChapter);
            data.put("endChapter", endChapter);
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            return success("区间重生任务已入队并启动，可在重启后恢复", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", e.getMessage());
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
