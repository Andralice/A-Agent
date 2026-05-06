package com.start.agent.repository;

import com.start.agent.model.NovelCharacterState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NovelCharacterStateRepository extends JpaRepository<NovelCharacterState, Long> {

    List<NovelCharacterState> findByNovelId(Long novelId);

    Optional<NovelCharacterState> findByNovelIdAndCharacterKey(Long novelId, String characterKey);

    List<NovelCharacterState> findByNovelIdAndCharacterKeyIn(Long novelId, Collection<String> keys);
}
