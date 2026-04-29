package com.start.agent.Service;

import com.start.agent.model.Novel;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CleanupService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;

    public CleanupService(NovelRepository novelRepository, ChapterRepository chapterRepository) {
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        log.info("【服务初始化】CleanupService 已就绪");
    }

    /**
     * 每天凌晨2点执行清理任务
     * 清理创建超过24小时且没有章节的空白小说
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupEmptyNovels() {
        log.info("【🧹 清理任务】========== 开始清理空白小说 ==========");
        long startTime = System.currentTimeMillis();
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            
            List<Novel> emptyNovels = novelRepository.findOldNovelsWithoutChapters(cutoffTime);
            
            if (emptyNovels.isEmpty()) {
                log.info("【🧹 清理任务】✅ 没有需要清理的空白小说");
                return;
            }
            
            log.info("【🧹 清理任务】发现 {} 个需要清理的空白小说", emptyNovels.size());
            
            int cleanedCount = 0;
            for (Novel novel : emptyNovels) {
                try {
                    log.debug("【🧹 清理任务】删除空白小说 - ID: {}, 标题: {}, 创建时间: {}", 
                            novel.getId(), novel.getTitle(), novel.getCreateTime());
                    novelRepository.delete(novel);
                    cleanedCount++;
                } catch (Exception e) {
                    log.error("【🧹 清理任务】删除小说失败 - ID: {}", novel.getId(), e);
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【🧹 清理任务】✅ 清理完成 - 共删除 {} 个空白小说，耗时: {}ms", cleanedCount, elapsed);
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【🧹 清理任务】❌ 清理任务执行异常 - 耗时: {}ms", elapsed, e);
        }
    }

    /**
     * 每小时执行一次快速检查（可选）
     * 清理创建超过1小时的空白小说，用于更频繁的清理
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void quickCleanupEmptyNovels() {
        log.debug("【⚡ 快速清理】检查空白小说...");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
            List<Novel> emptyNovels = novelRepository.findOldNovelsWithoutChapters(cutoffTime);
            
            if (!emptyNovels.isEmpty()) {
                log.info("【⚡ 快速清理】发现 {} 个超过1小时的空白小说，将在下次完整清理时处理", emptyNovels.size());
            }
        } catch (Exception e) {
            log.error("【⚡ 快速清理】检查失败", e);
        }
    }

    /**
     * 手动触发清理任务（可通过API调用）
     */
    @Transactional
    public int manualCleanupEmptyNovels() {
        log.info("【🔧 手动清理】========== 手动触发清理空白小说 ==========");
        long startTime = System.currentTimeMillis();
        
        try {
            List<Novel> emptyNovels = novelRepository.findNovelsWithoutChapters();
            
            if (emptyNovels.isEmpty()) {
                log.info("【🔧 手动清理】✅ 没有需要清理的空白小说");
                return 0;
            }
            
            log.info("【🔧 手动清理】发现 {} 个空白小说", emptyNovels.size());
            
            int cleanedCount = 0;
            for (Novel novel : emptyNovels) {
                try {
                    novelRepository.delete(novel);
                    cleanedCount++;
                } catch (Exception e) {
                    log.error("【🔧 手动清理】删除小说失败 - ID: {}", novel.getId(), e);
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【🔧 手动清理】✅ 清理完成 - 共删除 {} 个空白小说，耗时: {}ms", cleanedCount, elapsed);
            
            return cleanedCount;
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【🔧 手动清理】❌ 清理任务执行异常 - 耗时: {}ms", elapsed, e);
            return 0;
        }
    }
}
