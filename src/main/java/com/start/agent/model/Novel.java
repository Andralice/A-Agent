package com.start.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说聚合根实体：标题、题材、生成设定、文风流水线、写作工作台状态、全书热梗开关等。
 */
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

    /**
     * 两阶段大纲时的「冲突图谱」JSON（第一阶段）；与 {@link #description} 配套，供复盘与前端展示。
     * 单阶段生成或未启用图谱时为 null。
     */
    @Column(name = "outline_graph_json", columnDefinition = "TEXT")
    private String outlineGraphJson;

    @Column(nullable = false)
    private String topic;

    @Column(name = "generation_setting", columnDefinition = "TEXT")
    private String generationSetting;

    @Column(name = "writing_pipeline", length = 40)
    private String writingPipeline;

    /** 是否在正文适当位置少量使用网络热梗（前端可开关；默认关闭）。 */
    @Column(name = "hot_meme_enabled", nullable = false, columnDefinition = "tinyint(1) NOT NULL DEFAULT 0")
    private boolean hotMemeEnabled = false;

    /**
     * 可选全书参数 JSON：{@link com.start.agent.model.WritingStyleHints}、{@code narrative}、{@code cognitionArc}、
     * 文笔四层 {@code rhythm}/{@code perception}/{@code language}/{@code informationFlow} 等可并存；缺省或无效则不追加对应提示。
     */
    @Column(name = "writing_style_params", columnDefinition = "TEXT")
    private String writingStyleParams;

    /** 连载平台（如起点、晋江、自建站等），仅供前端展示与备注。 */
    @Column(name = "serialization_platform", length = 200)
    private String serializationPlatform;

    /** 创作说明：本书用途、受众、备注（区别于 AI 生成的大纲正文 description）。 */
    @Column(name = "creator_note", columnDefinition = "TEXT")
    private String creatorNote;

    /**
     * 是否在 HTTP 书库中对匿名访客可见；false 时仅持有管理员 JWT 的用户可访问该书详情与章节接口。
     * QQ 机器人等直连 {@link com.start.agent.service.NovelAgentService} 的路径不受此字段限制。
     */
    @Column(name = "library_public", nullable = false, columnDefinition = "tinyint(1) NOT NULL DEFAULT 1")
    private boolean libraryPublic = true;

    /**
     * 生成大纲时「开篇逐章细纲」下限章数；null 表示使用服务端 {@code novel.outline.detailed-prefix-chapters}。
     */
    @Column(name = "outline_detailed_prefix_chapters")
    private Integer outlineDetailedPrefixChapters;

    /**
     * 生成大纲时全书路线图覆盖的末章号下限；null 表示使用服务端 {@code novel.outline.min-roadmap-chapters}。
     */
    @Column(name = "outline_min_roadmap_chapters")
    private Integer outlineMinRoadmapChapters;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * M4：上一章成功落库后写入的「下一章开篇承接提示」（衔接锚点 + 尾声摘录等）；生成下一章初稿时注入。
     * 关闭 {@code novel.narrative-engine.m4-carryover-enabled} 时不更新、不注入。
     */
    @Column(name = "narrative_carryover", columnDefinition = "TEXT")
    private String narrativeCarryover;

    /**
     * M9：跨章叙事状态快照 JSON（衔接锚点、侧车事实摘要、阶段快照摘录等）；与 {@code chapter_fact}、{@code plot_snapshot}、M4 承接同源聚合，供只读 API。
     */
    @Column(name = "narrative_state_json", columnDefinition = "TEXT")
    private String narrativeStateJson;

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
