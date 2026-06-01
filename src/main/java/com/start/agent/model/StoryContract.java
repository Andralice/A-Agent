package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 故事契约：主线真源链路 MASTER_SETTING → VOLUME → CHAPTER → REVIEW → CHAPTER_COMMIT。
 * 每条契约为结构化 JSON，仅做增量写入，不重写整份设定。
 */
@Data
@Entity
@Table(name = "story_contract", indexes = {
        @Index(name = "idx_sc_novel", columnList = "novel_id"),
        @Index(name = "idx_sc_novel_type", columnList = "novel_id, contract_type")
})
public class StoryContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "contract_type", nullable = false, length = 32)
    private String contractType;

    @Column(name = "volume_number")
    private Integer volumeNumber;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "status", nullable = false, length = 24, columnDefinition = "varchar(24) NOT NULL DEFAULT 'DRAFT'")
    private String status = StoryContractStatus.DRAFT.name();

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) status = StoryContractStatus.DRAFT.name();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
