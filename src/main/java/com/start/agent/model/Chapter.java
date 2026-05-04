package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 单章实体：正文、章节号、本章附加设定及章节级写作状态。
 */
@Data
@Entity
@Table(name = "chapter", indexes = {
        @Index(name = "idx_chapter_novel_number", columnList = "novel_id, chapter_number", unique = true)
})
public class Chapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "generation_setting", columnDefinition = "TEXT")
    private String generationSetting;

    /**
     * M7：叙事引擎中间产物 JSON（Planner 摘要 / Lint 命中与修正标记）；只读 API 暴露给前端调试。
     */
    @Column(name = "narrative_engine_artifact", columnDefinition = "TEXT")
    private String narrativeEngineArtifact;

    /** {@link ChapterWriteState}（库表默认 READY，兼容旧数据行）。 */
    @Column(name = "write_state", nullable = false, length = 48, columnDefinition = "varchar(48) NOT NULL DEFAULT 'READY'")
    private String writeState = ChapterWriteState.READY.name();

    @Column(name = "write_state_updated_at")
    private LocalDateTime writeStateUpdatedAt;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (writeState == null || writeState.isBlank()) {
            writeState = ChapterWriteState.READY.name();
        }
        writeStateUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onChapterPersistUpdate() {
        writeStateUpdatedAt = LocalDateTime.now();
    }
}
