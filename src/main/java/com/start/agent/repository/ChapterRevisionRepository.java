package com.start.agent.repository;

import com.start.agent.model.ChapterRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRevisionRepository extends JpaRepository<ChapterRevision, Long> {

    List<ChapterRevision> findByNovelIdAndChapterNumberOrderByRevisionNumberDesc(Long novelId, Integer chapterNumber);

    List<ChapterRevision> findByNovelIdOrderByRevisionNumberDesc(Long novelId);

    Optional<ChapterRevision> findTopByNovelIdAndChapterNumberOrderByRevisionNumberDesc(Long novelId, Integer chapterNumber);

    int countByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
}
