package com.start.agent.repository;

import com.start.agent.model.CharacterProfileRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterProfileRevisionRepository extends JpaRepository<CharacterProfileRevision, Long> {

    List<CharacterProfileRevision> findByNovelIdAndCharacterProfileIdOrderByRevisionNumberDesc(Long novelId, Long characterProfileId);

    int countByNovelIdAndCharacterProfileId(Long novelId, Long characterProfileId);
}
