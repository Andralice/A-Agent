package com.start.agent.repository;

import com.start.agent.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

/** {@link com.start.agent.model.Chapter} 持久化与批量状态更新查询。 */
@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByNovelIdOrderByChapterNumberAsc(Long novelId);
    Optional<Chapter> findByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Chapter c SET c.writeState = :st WHERE c.novelId = :novelId AND c.chapterNumber >= :fromInclusive AND c.chapterNumber <= :toInclusive")
    int bulkSetWriteStateInRange(@Param("novelId") Long novelId,
                                 @Param("fromInclusive") int fromInclusive,
                                 @Param("toInclusive") int toInclusive,
                                 @Param("st") String writeState);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Chapter c SET c.writeState = :ready WHERE c.novelId = :novelId")
    int resetWriteStateForNovel(@Param("novelId") Long novelId, @Param("ready") String ready);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Chapter c SET c.writeState = :ready")
    void resetWriteStateGlobally(@Param("ready") String ready);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Chapter c WHERE c.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
