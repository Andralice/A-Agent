package com.start.agent.repository;


import com.start.agent.model.CharacterProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@link com.start.agent.model.CharacterProfile} 持久化。 */
@Repository
public interface CharacterProfileRepository extends JpaRepository<CharacterProfile, Long> {
    
    List<CharacterProfile> findByNovelId(Long novelId);
    
    Optional<CharacterProfile> findByNovelIdAndCharacterName(Long novelId, String characterName);
    
    List<CharacterProfile> findByNovelIdAndCharacterType(Long novelId, String characterType);
    
    @Query("SELECT cp FROM CharacterProfile cp WHERE cp.novelId = :novelId ORDER BY cp.createTime ASC")
    List<CharacterProfile> findProfilesByNovelIdOrdered(@Param("novelId") Long novelId);
    
    void deleteByNovelId(Long novelId);
}
