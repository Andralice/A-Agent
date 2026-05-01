package com.start.agent.service;

import com.start.agent.model.UserStatistics;
import com.start.agent.repository.UserStatisticsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserStatisticsService {
    private final UserStatisticsRepository userStatisticsRepository;
    public UserStatisticsService(UserStatisticsRepository userStatisticsRepository) { this.userStatisticsRepository = userStatisticsRepository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateGroupStatistics(Long groupId, int chapterCount, int wordCount, boolean isNewNovel) {
        try {
            UserStatistics stats = userStatisticsRepository.findByGroupId(groupId).orElseGet(() -> { UserStatistics s = new UserStatistics(); s.setGroupId(groupId); s.setNovelCount(0); s.setChapterCount(0); s.setTotalWords(0L); s.setApiCallCount(0); return s; });
            if (isNewNovel) stats.setNovelCount(stats.getNovelCount() + 1);
            stats.setChapterCount(stats.getChapterCount() + chapterCount);
            stats.setTotalWords(stats.getTotalWords() + wordCount);
            stats.setApiCallCount(stats.getApiCallCount() + 1);
            stats.setLastActiveTime(LocalDateTime.now());
            userStatisticsRepository.save(stats);
        } catch (Exception e) { log.error("【统计数据】❌ 更新统计数据失败", e); }
    }
}
