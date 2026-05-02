package com.start.agent.model;

/** 异步生成任务在库中的生命周期状态。 */
public enum GenerationTaskStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}
