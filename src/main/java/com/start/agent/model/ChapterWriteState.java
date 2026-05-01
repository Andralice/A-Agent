package com.start.agent.model;

/** 单章在当前写作流水线中的占位状态（与小说级 phase 配套）。 */
public enum ChapterWriteState {
    /** 正常可读，未被当前批次保留。 */
    READY,

    /**
     * 本章已存在于库中，且处于某次写入任务锁定的区间内；在该任务结束之前视为「可能被重写或等待轮转」。
     */
    RANGE_RESERVED,

    /** 模型正在对本章做一次完整生成流水线（光标所在章）。 */
    GENERATING_ACTIVE
}
