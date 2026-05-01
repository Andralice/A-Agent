package com.start.agent.config;

import com.start.agent.service.WritingProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 可选：仅在配置 novel.writing-state.reset-on-boot=true 时清空 DB 的写作标记。
 * 默认不启用，便于进程崩溃/滚动发布后仍能通过库表看到上一次「自称进行中」的记录（请以业务为准复检）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "novel.writing-state.reset-on-boot", havingValue = "true")
public class WritingProgressColdStartCleaner implements ApplicationListener<ApplicationReadyEvent> {

    private final WritingProgressService writingProgressService;

    public WritingProgressColdStartCleaner(WritingProgressService writingProgressService) {
        this.writingProgressService = writingProgressService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            writingProgressService.reconcileOnColdStart();
        } catch (Exception e) {
            log.error("【写作状态】冷启动对齐失败", e);
        }
    }
}
