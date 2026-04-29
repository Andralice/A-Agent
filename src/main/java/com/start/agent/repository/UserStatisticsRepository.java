
package com.start.agent.repository;

import com.start.agent.model.UserStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStatisticsRepository extends JpaRepository<UserStatistics, Long> {
    
    Optional<UserStatistics> findByGroupId(Long groupId);
    
    Optional<UserStatistics> findByGroupIdAndUserId(Long groupId, Long userId);
    
    List<UserStatistics> findByGroupIdOrderByLastActiveTimeDesc(Long groupId);
    
    @Query("SELECT us FROM UserStatistics us WHERE us.groupId = :groupId AND us.userId IS NULL")
    Optional<UserStatistics> findGroupStatistics(@Param("groupId") Long groupId);
}
