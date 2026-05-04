
package com.start.agent.repository;

import com.start.agent.model.GenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@link com.start.agent.model.GenerationLog} 持久化。 */
@Repository
public interface GenerationLogRepository extends JpaRepository<GenerationLog, Long> {
    
    List<GenerationLog> findByNovelId(Long novelId);
    
    List<GenerationLog> findByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
    
    List<GenerationLog> findByGenerationType(String generationType);
    
    List<GenerationLog> findByStatus(String status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GenerationLog g WHERE g.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
