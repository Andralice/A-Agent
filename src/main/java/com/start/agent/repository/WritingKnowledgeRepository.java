package com.start.agent.repository;

import com.start.agent.model.WritingKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WritingKnowledgeRepository extends JpaRepository<WritingKnowledge, Long> {
    List<WritingKnowledge> findByTableName(String tableName);
    List<WritingKnowledge> findByTableNameAndCategory(String tableName, String category);

    @Query(value = "SELECT * FROM writing_knowledge WHERE table_name = :tableName AND MATCH(content) AGAINST(:query IN BOOLEAN MODE) LIMIT :limit",
           nativeQuery = true)
    List<WritingKnowledge> fullTextSearch(@Param("tableName") String tableName, @Param("query") String query, @Param("limit") int limit);

    @Query(value = "SELECT * FROM writing_knowledge WHERE MATCH(content) AGAINST(:query IN BOOLEAN MODE) LIMIT :limit",
           nativeQuery = true)
    List<WritingKnowledge> fullTextSearchAll(@Param("query") String query, @Param("limit") int limit);
}
