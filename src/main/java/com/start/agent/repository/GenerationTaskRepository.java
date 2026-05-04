package com.start.agent.repository;

import com.start.agent.model.GenerationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** {@link com.start.agent.model.GenerationTask} 持久化与按状态查询。 */
@Repository
public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {
    List<GenerationTask> findByNovelIdOrderByCreateTimeDesc(Long novelId);
    List<GenerationTask> findByStatusInOrderByCreateTimeAsc(Collection<String> statuses);
    List<GenerationTask> findByNovelIdAndStatusInOrderByCreateTimeAsc(Long novelId, Collection<String> statuses);

    @Query("SELECT COUNT(t) FROM GenerationTask t WHERE t.novelId = :novelId "
            + "AND t.status IN :statuses AND t.taskType IN :taskTypes")
    long countActiveByNovelAndTaskTypes(@Param("novelId") Long novelId,
                                        @Param("statuses") Collection<String> statuses,
                                        @Param("taskTypes") Collection<String> taskTypes);

    @Query("SELECT COUNT(t) FROM GenerationTask t WHERE t.novelId = :novelId " +
            "AND t.status IN :statuses " +
            "AND t.rangeFrom IS NOT NULL AND t.rangeTo IS NOT NULL " +
            "AND NOT (t.rangeTo < :fromChapter OR t.rangeFrom > :toChapter)")
    long countActiveOverlap(@Param("novelId") Long novelId,
                            @Param("fromChapter") int fromChapter,
                            @Param("toChapter") int toChapter,
                            @Param("statuses") Collection<String> statuses);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GenerationTask t SET t.status = :running, t.startedAt = :now, t.heartbeatAt = :now " +
            "WHERE t.id = :taskId AND t.status = :pending")
    int claimTaskForRunning(@Param("taskId") Long taskId,
                            @Param("pending") String pending,
                            @Param("running") String running,
                            @Param("now") java.time.LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GenerationTask t WHERE t.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
