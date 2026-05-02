package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一致性告警记录：某章检测到的设定冲突或风险描述，供监控与排查。
 */
@Data
@Entity
@Table(name = "consistency_alert", indexes = {
        @Index(name = "idx_alert_novel_chapter", columnList = "novel_id, chapter_number")
})
public class ConsistencyAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "auto_fix_attempted")
    private Boolean autoFixAttempted;

    @Column(name = "auto_fix_success")
    private Boolean autoFixSuccess;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
