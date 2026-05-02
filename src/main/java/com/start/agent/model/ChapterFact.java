package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节事实表侧车数据：从正文抽取的实体/事件等结构化片段，用于记忆与新角色发现。
 */
@Data
@Entity
@Table(name = "chapter_fact", indexes = {
        @Index(name = "idx_fact_novel_chapter", columnList = "novel_id, chapter_number")
})
public class ChapterFact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    @Column(name = "fact_type", nullable = false, length = 50)
    private String factType;

    @Column(name = "subject_name", length = 100)
    private String subjectName;

    @Column(name = "fact_content", columnDefinition = "TEXT")
    private String factContent;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
