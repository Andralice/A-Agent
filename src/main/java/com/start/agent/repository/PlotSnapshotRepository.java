package com.start.agent.repository;

import com.start.agent.model.PlotSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@link com.start.agent.model.PlotSnapshot} 持久化。 */
@Repository
public interface PlotSnapshotRepository extends JpaRepository<PlotSnapshot, Long> {
    Optional<PlotSnapshot> findTopByNovelIdOrderBySnapshotChapterDesc(Long novelId);
    List<PlotSnapshot> findByNovelIdOrderBySnapshotChapterDesc(Long novelId);
}
