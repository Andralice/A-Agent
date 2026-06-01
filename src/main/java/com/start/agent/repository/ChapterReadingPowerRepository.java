package com.start.agent.repository;

import com.start.agent.model.ChapterReadingPower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterReadingPowerRepository extends JpaRepository<ChapterReadingPower, Long> {
    Optional<ChapterReadingPower> findByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
    List<ChapterReadingPower> findByNovelIdOrderByChapterNumberAsc(Long novelId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChapterReadingPower crp WHERE crp.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
