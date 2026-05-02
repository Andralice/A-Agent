package com.start.agent.model;

/**
 * 异步任务业务类型：开书引导、单章续写、区间自动续写、区间重生等。
 */
public enum GenerationTaskType {
    INITIAL_BOOTSTRAP,
    CONTINUE_SINGLE,
    AUTO_CONTINUE_RANGE,
    REGENERATE_RANGE
}
