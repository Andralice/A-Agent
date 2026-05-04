package com.start.agent.repository;

import com.start.agent.model.ChapterFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@link com.start.agent.model.ChapterFact} 持久化。 */
@Repository
public interface ChapterFactRepository extends JpaRepository<ChapterFact, Long> {
    List<ChapterFact> findByNovelIdOrderByChapterNumberAscCreateTimeAsc(Long novelId);
    List<ChapterFact> findByNovelIdAndChapterNumberOrderByCreateTimeAsc(Long novelId, Integer chapterNumber);
    void deleteByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChapterFact f WHERE f.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);

    @Query("SELECT f.subjectName, COUNT(f) FROM ChapterFact f " +
            "WHERE f.novelId = :novelId AND f.factType = :factType AND f.chapterNumber >= :fromChapter " +
            "GROUP BY f.subjectName")
    List<Object[]> countBySubjectNameSince(@Param("novelId") Long novelId,
                                          @Param("factType") String factType,
                                          @Param("fromChapter") Integer fromChapter);
}
