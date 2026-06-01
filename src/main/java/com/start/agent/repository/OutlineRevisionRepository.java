package com.start.agent.repository;

import com.start.agent.model.OutlineRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutlineRevisionRepository extends JpaRepository<OutlineRevision, Long> {

    List<OutlineRevision> findByNovelIdOrderByRevisionNumberDesc(Long novelId);

    int countByNovelId(Long novelId);
}
