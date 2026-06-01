package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 写作知识库条目：对应 webnovel-writer 的 9 张 CSV 知识表，提供领域知识检索增强生成。
 */
@Data
@Entity
@Table(name = "writing_knowledge", indexes = {
        @Index(name = "idx_wk_table", columnList = "table_name"),
        @Index(name = "idx_wk_category", columnList = "category")
})
public class WritingKnowledge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false, length = 64)
    private String tableName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 128)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
