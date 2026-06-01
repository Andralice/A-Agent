package com.start.agent.repository;

import com.start.agent.model.Foreshadowing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ForeshadowingRepository extends JpaRepository<Foreshadowing, Long> {
    List<Foreshadowing> findByNovelIdOrderByPlantedChapterAsc(Long novelId);
    List<Foreshadowing> findByNovelIdAndStatus(Long novelId, String status);
    List<Foreshadowing> findByNovelIdAndStatusNot(Long novelId, String status);

    @Query("SELECT f FROM Foreshadowing f WHERE f.novelId = :novelId AND f.status IN :statuses ORDER BY " +
           "CASE f.urgency WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END, " +
           "f.deadlineChapter ASC NULLS LAST")
    List<Foreshadowing> findUrgentLoops(@Param("novelId") Long novelId, @Param("statuses") List<String> statuses);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Foreshadowing f WHERE f.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
