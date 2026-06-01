package com.start.agent.repository;

import com.start.agent.model.StoryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StoryContractRepository extends JpaRepository<StoryContract, Long> {
    List<StoryContract> findByNovelIdOrderByCreateTimeAsc(Long novelId);
    List<StoryContract> findByNovelIdAndContractType(Long novelId, String contractType);
    List<StoryContract> findByNovelIdAndContractTypeAndVolumeNumber(Long novelId, String contractType, Integer volumeNumber);
    List<StoryContract> findByNovelIdAndContractTypeAndChapterNumber(Long novelId, String contractType, Integer chapterNumber);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StoryContract sc SET sc.status = 'SUPERSEDED' WHERE sc.novelId = :novelId AND sc.contractType = :contractType AND sc.status = 'ACTIVE'")
    int supersedeActive(@Param("novelId") Long novelId, @Param("contractType") String contractType);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM StoryContract sc WHERE sc.novelId = :novelId")
    void deleteAllByNovelId(@Param("novelId") Long novelId);
}
