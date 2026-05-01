package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "novel", indexes = {
        @Index(name = "idx_novel_group_id", columnList = "group_id"),
        @Index(name = "idx_novel_user_group", columnList = "user_id, group_id"),
        @Index(name = "idx_novel_write_phase", columnList = "write_phase")
})
public class Novel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String topic;

    @Column(name = "generation_setting", columnDefinition = "TEXT")
    private String generationSetting;

    @Column(name = "writing_pipeline", length = 40)
    private String writingPipeline;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** {@link NovelWritePhase}（库表默认 IDLE，兼容旧数据行）。 */
    @Column(name = "write_phase", nullable = false, length = 48, columnDefinition = "varchar(48) NOT NULL DEFAULT 'IDLE'")
    private String writePhase = NovelWritePhase.IDLE.name();

    /** 写入任务锁定区间下限（含），无任务时为 null。 */
    @Column(name = "write_range_from")
    private Integer writeRangeFrom;

    /** 写入任务锁定区间上限（含），无任务时为 null。 */
    @Column(name = "write_range_to")
    private Integer writeRangeTo;

    /** 当前模型正在输出的章节号，单章或与批量中的光标。 */
    @Column(name = "write_cursor_chapter")
    private Integer writeCursorChapter;

    /** 给人看的短语说明（如 regenerate 区间、auto-continue 目标等）。 */
    @Column(name = "write_phase_detail", length = 320)
    private String writePhaseDetail;

    @Column(name = "write_started_at")
    private LocalDateTime writeStartedAt;

    @Column(name = "write_updated_at")
    private LocalDateTime writeUpdatedAt;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (writePhase == null || writePhase.isBlank()) {
            writePhase = NovelWritePhase.IDLE.name();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
