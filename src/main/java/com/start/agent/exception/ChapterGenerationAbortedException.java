package com.start.agent.exception;

/**
 * 章节生成被协作式中止（例如用户在任务队列中点击「取消」），不代表模型或业务异常。
 */
public class ChapterGenerationAbortedException extends RuntimeException {

    public ChapterGenerationAbortedException() {
        super("chapter_generation_aborted");
    }
}
