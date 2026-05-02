package com.start.agent.service;

import com.start.agent.model.Novel;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 定时清理长期未产出章节的空壳小说等维护任务。 */
@Slf4j
@Service
public class CleanupService {
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    public CleanupService(NovelRepository novelRepository, ChapterRepository chapterRepository) { this.novelRepository = novelRepository; this.chapterRepository = chapterRepository; }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupEmptyNovels() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            for (Novel novel : novelRepository.findOldNovelsWithoutChapters(cutoffTime)) novelRepository.delete(novel);
        } catch (Exception e) { log.error("【🧹 清理任务】❌ 清理任务执行异常", e); }
    }

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void quickCleanupEmptyNovels() {
        try { novelRepository.findOldNovelsWithoutChapters(LocalDateTime.now().minusHours(1)); }
        catch (Exception e) { log.error("【⚡ 快速清理】检查失败", e); }
    }

    @Transactional
    public int manualCleanupEmptyNovels() {
        try {
            List<Novel> emptyNovels = novelRepository.findNovelsWithoutChapters();
            for (Novel novel : emptyNovels) novelRepository.delete(novel);
            return emptyNovels.size();
        } catch (Exception e) { log.error("【🔧 手动清理】❌ 清理任务执行异常", e); return 0; }
    }
}