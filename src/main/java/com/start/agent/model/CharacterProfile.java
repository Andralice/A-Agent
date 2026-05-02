package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色档案实体：按小说维度存储姓名、设定 JSON 等，供生成与一致性约束使用。
 */
@Data
@Entity
@Table(name = "character_profile")
public class CharacterProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "character_name", nullable = false, length = 100)
    private String characterName;

    @Column(name = "character_type", nullable = false, length = 50)
    private String characterType;

    @Column(name = "profile_content", columnDefinition = "TEXT")
    private String profileContent;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
