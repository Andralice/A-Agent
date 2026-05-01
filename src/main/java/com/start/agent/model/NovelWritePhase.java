package com.start.agent.model;

/** 本书当前「写作工作台」所处阶段（面向监控与前端展示）。 */
public enum NovelWritePhase {
    /** 空闲，无持久化认为的进行中写作任务（进程重启后会统一回落为 IDLE）。 */
    IDLE,

    /** 首次创建流程：大纲、角色、前几章流水线。 */
    INITIAL_BOOTSTRAP,

    /** 单章续写（含 API 发起的下一章写入）。 */
    SINGLE_CONTINUE,

    /** 批量自动续写（目标章节区间）。 */
    AUTO_CONTINUE_RANGE,

    /** 单章重生或区间内逐章重写。 */
    REGENERATING_RANGE,

    /** 角色档案修复（不锁章节但与内容相关运维）。 */
    CHARACTER_MAINTENANCE
}
