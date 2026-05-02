package com.start.agent.repository;

import com.start.agent.model.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** {@link com.start.agent.model.Novel} 持久化。 */
@Repository
public interface NovelRepository extends JpaRepository<Novel, Long> {
    List<Novel> findByGroupId(Long groupId);
    List<Novel> findByUserIdAndGroupId(Long userId, Long groupId);
    
    @Query("SELECT n FROM Novel n WHERE n.id NOT IN (SELECT DISTINCT c.novelId FROM Chapter c)")
    List<Novel> findNovelsWithoutChapters();
    
    @Query("SELECT n FROM Novel n WHERE n.id NOT IN (SELECT DISTINCT c.novelId FROM Chapter c) AND n.createTime < :cutoffTime")
    List<Novel> findOldNovelsWithoutChapters(LocalDateTime cutoffTime);
}

