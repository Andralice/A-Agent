package com.start.agent.repository;

import com.start.agent.model.ConsistencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@link com.start.agent.model.ConsistencyAlert} 持久化。 */
@Repository
public interface ConsistencyAlertRepository extends JpaRepository<ConsistencyAlert, Long> {
    List<ConsistencyAlert> findByNovelIdOrderByCreateTimeDesc(Long novelId);
    List<ConsistencyAlert> findByNovelIdAndChapterNumberOrderByCreateTimeDesc(Long novelId, Integer chapterNumber);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ConsistencyAlert a WHERE a.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
