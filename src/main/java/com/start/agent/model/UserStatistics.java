
package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_statistics")
public class UserStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "novel_count")
    private Integer novelCount = 0;

    @Column(name = "chapter_count")
    private Integer chapterCount = 0;

    @Column(name = "total_words")
    private Long totalWords = 0L;

    @Column(name = "api_call_count")
    private Integer apiCallCount = 0;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        lastActiveTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        lastActiveTime = LocalDateTime.now();
    }
}
