
package com.start.agent.repository;

import com.start.agent.model.GenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenerationLogRepository extends JpaRepository<GenerationLog, Long> {
    
    List<GenerationLog> findByNovelId(Long novelId);
    
    List<GenerationLog> findByNovelIdAndChapterNumber(Long novelId, Integer chapterNumber);
    
    List<GenerationLog> findByGenerationType(String generationType);
    
    List<GenerationLog> findByStatus(String status);
}
