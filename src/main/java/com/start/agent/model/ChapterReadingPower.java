package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 单章阅读力指标：钩子类型/强度、爽点模式、微兑现、硬约束违规、读者债务。
 * 对应 webnovel-writer 的 Reading Power Taxonomy。
 */
@Data
@Entity
@Table(name = "chapter_reading_power", indexes = {
        @Index(name = "idx_crp_novel_chapter", columnList = "novel_id, chapter_number", unique = true)
})
public class ChapterReadingPower {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    @Column(name = "hook_type", length = 32)
    private String hookType;

    @Column(name = "hook_strength", length = 16)
    private String hookStrength;

    @Column(name = "cool_point_pattern", length = 48)
    private String coolPointPattern;

    @Column(name = "micro_payoffs", columnDefinition = "TEXT")
    private String microPayoffs;

    @Column(name = "hard_violations", columnDefinition = "TEXT")
    private String hardViolations;

    @Column(name = "reader_debt")
    private Integer readerDebt;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (readerDebt == null) readerDebt = 0;
    }
}
