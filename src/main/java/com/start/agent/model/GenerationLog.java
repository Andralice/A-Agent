
package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 单次生成调用日志：耗时、字数、成功失败等，用于统计与审计。
 */
@Data
@Entity
@Table(name = "generation_log")
public class GenerationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id")
    private Long novelId;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    @Column(name = "generation_type", nullable = false, length = 50)
    private String generationType;

    @Column(name = "prompt_length")
    private Integer promptLength;

    @Column(name = "response_length")
    private Integer responseLength;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
