package com.start.agent.service;

import com.start.agent.model.GenerationLog;
import com.start.agent.repository.GenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 落库 {@link com.start.agent.model.GenerationLog}，记录每次章节生成的耗时与结果。 */
@Slf4j
@Service
public class GenerationLogService {
    private final GenerationLogRepository generationLogRepository;
    public GenerationLogService(GenerationLogRepository generationLogRepository) { this.generationLogRepository = generationLogRepository; }

    @Transactional
    public void saveGenerationLog(Long novelId, Integer chapterNumber, String generationType, int responseLength, long elapsedMs, String status, String errorMessage) {
        try {
            GenerationLog logItem = new GenerationLog();
            logItem.setNovelId(novelId); logItem.setChapterNumber(chapterNumber); logItem.setGenerationType(generationType); logItem.setResponseLength(responseLength); logItem.setElapsedMs(elapsedMs); logItem.setStatus(status); logItem.setErrorMessage(errorMessage);
            generationLogRepository.save(logItem);
        } catch (Exception e) { log.error("【日志记录】❌ 保存生成日志失败", e); }
    }
}
