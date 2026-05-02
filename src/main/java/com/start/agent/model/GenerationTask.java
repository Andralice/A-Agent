package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可恢复的异步生成任务：类型、状态、章节区间等，用于进程重启后续跑。
 */
@Data
@Entity
@Table(name = "generation_task", indexes = {
        @Index(name = "idx_gen_task_novel", columnList = "novel_id"),
        @Index(name = "idx_gen_task_status", columnList = "status")
})
public class GenerationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "task_type", nullable = false, length = 48)
    private String taskType;

    @Column(name = "status", nullable = false, length = 32, columnDefinition = "varchar(32) NOT NULL DEFAULT 'PENDING'")
    private String status = GenerationTaskStatus.PENDING.name();

    @Column(name = "range_from")
    private Integer rangeFrom;

    @Column(name = "range_to")
    private Integer rangeTo;

    @Column(name = "current_chapter")
    private Integer currentChapter;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (retryCount == null) retryCount = 0;
        if (status == null || status.isBlank()) status = GenerationTaskStatus.PENDING.name();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
