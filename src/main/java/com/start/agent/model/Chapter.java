package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

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
