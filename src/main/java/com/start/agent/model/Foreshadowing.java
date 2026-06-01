package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 伏笔/开放环管理：埋设 → 提醒 → 回收 全生命周期追踪，支持紧急度评分与甘特图可视化。
 */
@Data
@Entity
@Table(name = "foreshadowing", indexes = {
        @Index(name = "idx_fs_novel", columnList = "novel_id"),
        @Index(name = "idx_fs_novel_status", columnList = "novel_id, status"),
        @Index(name = "idx_fs_novel_urgency", columnList = "novel_id, urgency")
})
public class Foreshadowing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "loop_type", length = 32)
    private String loopType;

    @Column(length = 24, columnDefinition = "varchar(24) NOT NULL DEFAULT 'low'")
    private String urgency = "low";

    @Column(length = 24, nullable = false, columnDefinition = "varchar(24) NOT NULL DEFAULT 'PLANTED'")
    private String status = ForeshadowingStatus.PLANTED.name();

    @Column(name = "planted_chapter")
    private Integer plantedChapter;

    @Column(name = "reminded_chapters", columnDefinition = "TEXT")
    private String remindedChapters;

    @Column(name = "payoff_chapter")
    private Integer payoffChapter;

    @Column(name = "deadline_chapter")
    private Integer deadlineChapter;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) status = ForeshadowingStatus.PLANTED.name();
        if (urgency == null || urgency.isBlank()) urgency = "low";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
