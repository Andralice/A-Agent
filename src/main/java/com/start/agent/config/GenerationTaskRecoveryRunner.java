package com.start.agent.config;

import com.start.agent.service.GenerationTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** 应用就绪后触发未完成生成任务的恢复续跑。 */
@Slf4j
@Component
public class GenerationTaskRecoveryRunner implements ApplicationListener<ApplicationReadyEvent> {
    private final GenerationTaskService generationTaskService;

    public GenerationTaskRecoveryRunner(GenerationTaskService generationTaskService) {
        this.generationTaskService = generationTaskService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            generationTaskService.resumeRecoverableTasks();
        } catch (Exception e) {
            log.error("recover generation tasks failed", e);
        }
    }
}
