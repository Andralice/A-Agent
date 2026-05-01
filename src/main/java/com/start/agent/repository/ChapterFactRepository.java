package com.start.agent.repository;

import com.start.agent.model.ChapterFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterFactRepository extends JpaRepository<ChapterFact, Long> {
    List<ChapterFact> findByNovelIdOrderByChapterNumberAscCreateTimeAsc(Long novelId);
    List<ChapterFact> findByNovelIdAndChapterNumberOrderByCreateTimeAsc(Long novelId, Integer chapterNumber);
    void deleteByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
}
