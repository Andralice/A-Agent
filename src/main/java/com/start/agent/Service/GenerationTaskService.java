package com.start.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.GenerationTask;
import com.start.agent.model.GenerationTaskStatus;
import com.start.agent.model.GenerationTaskType;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.GenerationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成任务编排与恢复：创建任务、更新进度、应用启动后续跑 PENDING/RUNNING、并发区间冲突检测辅助。
 */
@Slf4j
@Service
public class GenerationTaskService {
    private static final Long DEFAULT_GROUP_ID = 0L;
    private static final List<String> ACTIVE_STATUSES = List.of(
            GenerationTaskStatus.PENDING.name(),
            GenerationTaskStatus.RUNNING.name()
    );
    /** 与章节正文生成互斥的类型（不含大纲租约）。 */
    private static final List<String> CHAPTER_TASK_TYPES = List.of(
            GenerationTaskType.INITIAL_BOOTSTRAP.name(),
            GenerationTaskType.CONTINUE_SINGLE.name(),
            GenerationTaskType.AUTO_CONTINUE_RANGE.name(),
            GenerationTaskType.REGENERATE_RANGE.name()
    );

    private final GenerationTaskRepository generationTaskRepository;
    private final ChapterRepository chapterRepository;
    private final NovelAgentService novelAgentService;
    private final ObjectMapper objectMapper;
    private final Set<Long> inProcessTaskIds = ConcurrentHashMap.newKeySet();

    public GenerationTaskService(GenerationTaskRepository generationTaskRepository,
                                 ChapterRepository chapterRepository,
                                 NovelAgentService novelAgentService,
                                 ObjectMapper objectMapper) {
        this.generationTaskRepository = generationTaskRepository;
        this.chapterRepository = chapterRepository;
        this.novelAgentService = novelAgentService;
        this.objectMapper = objectMapper;
    }

    /**
     * 占用大纲重写 DB 租约（RUNNING），禁止并发章节类任务；调用方应先在本进程取得大纲区间锁，再调用本方法。
     *
     * @return 任务 id，结束时调用 {@link #releaseOutlineRegenerationLease(Long)}
     */
    @Transactional
    public Long attachOutlineRegenerationLease(Long novelId) {
        Objects.requireNonNull(novelId, "novelId");
        assertNoChapterGenerationLease(novelId);
        assertNoOutlineRegenerationLease(novelId);
        LocalDateTime now = LocalDateTime.now();
        GenerationTask task = new GenerationTask();
        task.setNovelId(novelId);
        task.setTaskType(GenerationTaskType.OUTLINE_REGENERATE.name());
        task.setRangeFrom(0);
        task.setRangeTo(0);
        task.setCurrentChapter(0);
        task.setPayloadJson("{}");
        task.setStatus(GenerationTaskStatus.RUNNING.name());
        task.setStartedAt(now);
        task.setHeartbeatAt(now);
        return generationTaskRepository.save(task).getId();
    }

    @Transactional
    public void releaseOutlineRegenerationLease(Long taskId) {
        if (taskId == null) return;
        generationTaskRepository.findById(taskId).ifPresent(t -> {
            if (GenerationTaskType.OUTLINE_REGENERATE.name().equals(t.getTaskType())) {
                markDone(taskId);
            }
        });
    }

    @Transactional(readOnly = true)
    public void assertNoChapterGenerationLease(Long novelId) {
        long n = generationTaskRepository.countActiveByNovelAndTaskTypes(novelId, ACTIVE_STATUSES, CHAPTER_TASK_TYPES);
        if (n > 0) {
            throw new IllegalStateException("本书存在进行中的章节生成任务，请等待结束后再修改大纲");
        }
    }

    @Transactional(readOnly = true)
    public void assertNoOutlineRegenerationLease(Long novelId) {
        long n = generationTaskRepository.countActiveByNovelAndTaskTypes(novelId, ACTIVE_STATUSES,
                List.of(GenerationTaskType.OUTLINE_REGENERATE.name()));
        if (n > 0) {
            throw new IllegalStateException("本书正在重新生成大纲，请稍后再提交章节类生成任务");
        }
    }

    public GenerationTask enqueueContinueTask(Long novelId, Integer chapterNumber, String generationSetting) {
        int resolvedChapter = chapterNumber == null
                ? chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId).size() + 1
                : chapterNumber;
        ensureNoActiveOverlap(novelId, resolvedChapter, resolvedChapter, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("generationSetting", generationSetting);
        payload.put("chapterNumber", resolvedChapter);
        GenerationTask task = new GenerationTask();
        task.setNovelId(novelId);
        task.setTaskType(GenerationTaskType.CONTINUE_SINGLE.name());
        task.setRangeFrom(resolvedChapter);
        task.setRangeTo(resolvedChapter);
        task.setCurrentChapter(resolvedChapter - 1);
        task.setPayloadJson(toJson(payload));
        task.setStatus(GenerationTaskStatus.PENDING.name());
        return generationTaskRepository.save(task);
    }

    public GenerationTask enqueueAutoContinueTask(Long novelId, Integer targetChapterCount, String generationSetting) {
        int current = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId).size();
        int target = targetChapterCount == null ? current + 1 : targetChapterCount;
        int from = current + 1;
        ensureNoActiveOverlap(novelId, from, target, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("generationSetting", generationSetting);
        payload.put("targetChapterCount", targetChapterCount);
        GenerationTask task = new GenerationTask();
        task.setNovelId(novelId);
        task.setTaskType(GenerationTaskType.AUTO_CONTINUE_RANGE.name());
        task.setRangeFrom(from);
        task.setRangeTo(target);
        task.setCurrentChapter(from - 1);
        task.setPayloadJson(toJson(payload));
        task.setStatus(GenerationTaskStatus.PENDING.name());
        return generationTaskRepository.save(task);
    }

    public GenerationTask enqueueRegenerateRangeTask(Long novelId, int startChapter, int endChapter, String generationSetting) {
        int from = Math.min(startChapter, endChapter);
        int to = Math.max(startChapter, endChapter);
        ensureNoActiveOverlap(novelId, from, to, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("generationSetting", generationSetting);
        payload.put("startChapter", startChapter);
        payload.put("endChapter", endChapter);
        GenerationTask task = new GenerationTask();
        task.setNovelId(novelId);
        task.setTaskType(GenerationTaskType.REGENERATE_RANGE.name());
        task.setRangeFrom(from);
        task.setRangeTo(to);
        task.setCurrentChapter(from - 1);
        task.setPayloadJson(toJson(payload));
        task.setStatus(GenerationTaskStatus.PENDING.name());
        return generationTaskRepository.save(task);
    }

    public GenerationTask enqueueInitialBootstrapTask(Long novelId, int targetChapterCount) {
        int target = Math.max(1, targetChapterCount);
        ensureNoActiveOverlap(novelId, 1, target, null);
        GenerationTask task = new GenerationTask();
        task.setNovelId(novelId);
        task.setTaskType(GenerationTaskType.INITIAL_BOOTSTRAP.name());
        task.setRangeFrom(1);
        task.setRangeTo(target);
        task.setCurrentChapter(0);
        task.setPayloadJson(toJson(Map.of("targetChapterCount", target)));
        task.setStatus(GenerationTaskStatus.PENDING.name());
        return generationTaskRepository.save(task);
    }

    public void executeAsync(Long taskId) {
        CompletableFuture.runAsync(() -> executeTask(taskId));
    }

    /** 运维按钮：若任务处于 PENDING，可手动触发一次执行。 */
    public boolean kickIfPending(Long taskId) {
        if (taskId == null) return false;
        return generationTaskRepository.findById(taskId).map(t -> {
            if (GenerationTaskStatus.PENDING.name().equals(t.getStatus())) {
                executeAsync(taskId);
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<GenerationTask> listTasksByNovel(Long novelId) {
        return generationTaskRepository.findByNovelIdOrderByCreateTimeDesc(novelId);
    }

    @Transactional(readOnly = true)
    public Optional<GenerationTask> getTask(Long taskId) {
        return generationTaskRepository.findById(taskId);
    }

    @Transactional
    public boolean cancelTask(Long taskId) {
        Optional<GenerationTask> opt = generationTaskRepository.findById(taskId);
        if (opt.isEmpty()) return false;
        GenerationTask t = opt.get();
        if (GenerationTaskStatus.DONE.name().equals(t.getStatus())) return false;
        t.setStatus(GenerationTaskStatus.CANCELLED.name());
        t.setFinishedAt(LocalDateTime.now());
        t.setHeartbeatAt(LocalDateTime.now());
        generationTaskRepository.save(t);
        return true;
    }

    @Transactional
    public Optional<GenerationTask> retryTask(Long taskId) {
        Optional<GenerationTask> opt = generationTaskRepository.findById(taskId);
        if (opt.isEmpty()) return Optional.empty();
        GenerationTask t = opt.get();
        if (!GenerationTaskStatus.FAILED.name().equals(t.getStatus())
                && !GenerationTaskStatus.CANCELLED.name().equals(t.getStatus())) {
            return Optional.empty();
        }
        if (t.getRangeFrom() != null && t.getRangeTo() != null) {
            ensureNoActiveOverlap(t.getNovelId(), t.getRangeFrom(), t.getRangeTo(), t.getId());
        }
        assertNoOutlineRegenerationLease(t.getNovelId());
        t.setStatus(GenerationTaskStatus.PENDING.name());
        t.setLastError(null);
        t.setFinishedAt(null);
        t.setHeartbeatAt(LocalDateTime.now());
        GenerationTask saved = generationTaskRepository.save(t);
        return Optional.of(saved);
    }

    public void resumeRecoverableTasks() {
        List<GenerationTask> tasks = generationTaskRepository.findByStatusInOrderByCreateTimeAsc(
                List.of(GenerationTaskStatus.PENDING.name(), GenerationTaskStatus.RUNNING.name()));
        for (GenerationTask task : tasks) {
            if (GenerationTaskStatus.RUNNING.name().equals(task.getStatus())) {
                markPending(task.getId(), "recovered_after_restart");
            }
            executeAsync(task.getId());
        }
    }

    public void executeTask(Long taskId) {
        if (taskId == null || !inProcessTaskIds.add(taskId)) return;
        try {
            Optional<GenerationTask> opt = generationTaskRepository.findById(taskId);
            if (opt.isEmpty()) return;
            GenerationTask task = opt.get();
            if (GenerationTaskStatus.DONE.name().equals(task.getStatus())
                    || GenerationTaskStatus.CANCELLED.name().equals(task.getStatus())
                    || GenerationTaskStatus.FAILED.name().equals(task.getStatus())) {
                return;
            }
            if (!tryClaimRunning(taskId)) {
                return;
            }
            task = generationTaskRepository.findById(taskId).orElse(null);
            if (task == null) return;
            LocalDateTime claimStamp = task.getStartedAt();
            if (claimStamp == null) {
                log.warn("generation task claimed but startedAt is null, skip run taskId={}", taskId);
                return;
            }
            boolean finishedAll = runTaskByType(task, claimStamp);
            if (finishedAll) {
                markDone(taskId);
            }
        } catch (Exception e) {
            log.error("generation task failed, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        } finally {
            inProcessTaskIds.remove(taskId);
        }
    }

    /**
     * @param claimStamp 本次成功 claim 后 DB 中的 {@link GenerationTask#getStartedAt()}，用于每章前识别是否仍由本执行持有（另一实例重新 claim 会刷新 startedAt）。
     * @return 全部章节（或 bootstrap）按预期跑完且未丢失 lease；否则不调用 {@link #markDone(Long)}。
     */
    private boolean runTaskByType(GenerationTask task, LocalDateTime claimStamp) {
        GenerationTaskType type = GenerationTaskType.valueOf(task.getTaskType());
        if (type == GenerationTaskType.OUTLINE_REGENERATE) {
            log.info("outline regeneration lease recovered/cleared by worker, taskId={}", task.getId());
            return true;
        }
        Long novelId = task.getNovelId();
        Map<String, Object> payload = parsePayload(task.getPayloadJson());
        String generationSetting = payload.get("generationSetting") == null ? null : String.valueOf(payload.get("generationSetting"));
        int from = task.getRangeFrom() == null ? 1 : task.getRangeFrom();
        int to = task.getRangeTo() == null ? from : task.getRangeTo();
        int start = task.getCurrentChapter() == null ? from : Math.max(from, task.getCurrentChapter() + 1);
        if (type == GenerationTaskType.INITIAL_BOOTSTRAP) {
            if (!stillOwnsLease(task.getId(), claimStamp)) {
                log.info("generation task lease lost before bootstrap, taskId={}", task.getId());
                return false;
            }
            int target = payload.get("targetChapterCount") instanceof Number ? ((Number) payload.get("targetChapterCount")).intValue() : to;
            // 先补齐 outline + profile，再按章补齐到目标章节
            novelAgentService.bootstrapNovel(DEFAULT_GROUP_ID, novelId, Math.max(1, target));
            if (!stillOwnsLease(task.getId(), claimStamp)) {
                log.info("generation task lease lost after bootstrap, taskId={}", task.getId());
                return false;
            }
            updateHeartbeat(task.getId(), Math.max(0, target));
            return stillOwnsLease(task.getId(), claimStamp);
        }
        for (int chapter = start; chapter <= to; chapter++) {
            if (!stillOwnsLease(task.getId(), claimStamp)) {
                log.info("generation task lease lost mid-run, taskId={}, stop before chapter={}", task.getId(), chapter);
                return false;
            }
            if (isCancelled(task.getId())) {
                log.info("generation task cancelled mid-run, taskId={}, stop at chapter={}", task.getId(), chapter);
                return false;
            }
            switch (type) {
                case CONTINUE_SINGLE, AUTO_CONTINUE_RANGE ->
                        novelAgentService.continueChapter(DEFAULT_GROUP_ID, novelId.intValue(), chapter, generationSetting);
                case REGENERATE_RANGE ->
                        novelAgentService.regenerateChapter(DEFAULT_GROUP_ID, novelId.intValue(), chapter, generationSetting);
            }
            updateHeartbeat(task.getId(), chapter);
        }
        return stillOwnsLease(task.getId(), claimStamp);
    }

    /** 仍为 RUNNING 且 startedAt 与本次 claim 一致，表示未被另一实例重新领取。 */
    private boolean stillOwnsLease(Long taskId, LocalDateTime claimStartedAt) {
        if (taskId == null || claimStartedAt == null) return false;
        return generationTaskRepository.findById(taskId)
                .map(t -> GenerationTaskStatus.RUNNING.name().equals(t.getStatus())
                        && Objects.equals(claimStartedAt, t.getStartedAt()))
                .orElse(false);
    }

    @Transactional
    protected void markPending(Long taskId, String reason) {
        generationTaskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(GenerationTaskStatus.PENDING.name());
            t.setLastError(reason);
            t.setHeartbeatAt(LocalDateTime.now());
            generationTaskRepository.save(t);
        });
    }

    @Transactional
    protected void updateHeartbeat(Long taskId, int currentChapter) {
        generationTaskRepository.findById(taskId).ifPresent(t -> {
            t.setCurrentChapter(currentChapter);
            t.setHeartbeatAt(LocalDateTime.now());
            generationTaskRepository.save(t);
        });
    }

    @Transactional
    protected void markDone(Long taskId) {
        generationTaskRepository.findById(taskId).ifPresent(t -> {
            if (GenerationTaskStatus.CANCELLED.name().equals(t.getStatus())) return;
            t.setStatus(GenerationTaskStatus.DONE.name());
            t.setFinishedAt(LocalDateTime.now());
            t.setHeartbeatAt(LocalDateTime.now());
            generationTaskRepository.save(t);
        });
    }

    @Transactional
    protected void markFailed(Long taskId, String error) {
        generationTaskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(GenerationTaskStatus.FAILED.name());
            t.setLastError(error);
            t.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
            t.setHeartbeatAt(LocalDateTime.now());
            generationTaskRepository.save(t);
        });
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isCancelled(Long taskId) {
        return generationTaskRepository.findById(taskId)
                .map(t -> GenerationTaskStatus.CANCELLED.name().equals(t.getStatus()))
                .orElse(true);
    }

    @Transactional
    protected boolean tryClaimRunning(Long taskId) {
        int updated = generationTaskRepository.claimTaskForRunning(
                taskId,
                GenerationTaskStatus.PENDING.name(),
                GenerationTaskStatus.RUNNING.name(),
                LocalDateTime.now()
        );
        if (updated > 0) return true;
        return generationTaskRepository.findById(taskId)
                .map(t -> GenerationTaskStatus.RUNNING.name().equals(t.getStatus()))
                .orElse(false);
    }

    private void ensureNoActiveOverlap(Long novelId, int from, int to, Long ignoreTaskId) {
        assertNoOutlineRegenerationLease(novelId);
        int lo = Math.min(from, to);
        int hi = Math.max(from, to);
        long overlap = generationTaskRepository.countActiveOverlap(novelId, lo, hi, ACTIVE_STATUSES);
        if (overlap <= 0) return;
        if (ignoreTaskId != null) {
            Optional<GenerationTask> self = generationTaskRepository.findById(ignoreTaskId);
            if (self.isPresent()
                    && self.get().getNovelId().equals(novelId)
                    && self.get().getRangeFrom() != null
                    && self.get().getRangeTo() != null
                    && self.get().getRangeFrom().equals(lo)
                    && self.get().getRangeTo().equals(hi)
                    && ACTIVE_STATUSES.contains(self.get().getStatus())) {
                overlap -= 1;
            }
        }
        if (overlap > 0) {
            throw new IllegalStateException("目标区间已有进行中的任务，请稍后重试");
        }
    }
}
