package com.start.agent.repository;

import com.start.agent.model.PlotSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link com.start.agent.model.PlotSnapshot} 持久化。 */
@Repository
public interface PlotSnapshotRepository extends JpaRepository<PlotSnapshot, Long> {
    Optional<PlotSnapshot> findTopByNovelIdOrderBySnapshotChapterDesc(Long novelId);
    List<PlotSnapshot> findByNovelIdOrderBySnapshotChapterDesc(Long novelId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PlotSnapshot p WHERE p.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
