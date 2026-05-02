package com.start.agent.repository;

import com.start.agent.model.ConsistencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** {@link com.start.agent.model.ConsistencyAlert} 持久化。 */
@Repository
public interface ConsistencyAlertRepository extends JpaRepository<ConsistencyAlert, Long> {
    List<ConsistencyAlert> findByNovelIdOrderByCreateTimeDesc(Long novelId);
    List<ConsistencyAlert> findByNovelIdAndChapterNumberOrderByCreateTimeDesc(Long novelId, Integer chapterNumber);
}
