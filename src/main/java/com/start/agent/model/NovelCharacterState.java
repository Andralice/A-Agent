package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书本维度角色动态状态（叙事调度计划配套）；与 {@link CharacterProfile} 静态档案并列。
 * {@code characterKey} 与档案姓名同一规范化口径，便于按章检索与注入。
 */
@Data
@Entity
@Table(name = "novel_character_state", indexes = {
        @Index(name = "idx_ncs_novel_key", columnList = "novel_id, character_key", unique = true)
})
public class NovelCharacterState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "character_key", nullable = false, length = 100)
    private String characterKey;

    /** 柔性 JSON：态度、近期记忆、目标、情绪标签等，见 note 计划文档。 */
    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "source_chapter")
    private Integer sourceChapter;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
