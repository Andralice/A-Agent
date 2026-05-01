package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "plot_snapshot", indexes = {
        @Index(name = "idx_snapshot_novel_chapter", columnList = "novel_id, snapshot_chapter")
})
public class PlotSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "snapshot_chapter", nullable = false)
    private Integer snapshotChapter;

    @Column(name = "key_characters", length = 500)
    private String keyCharacters;

    @Column(name = "snapshot_content", columnDefinition = "TEXT")
    private String snapshotContent;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
