package com.start.agent.service;

import com.start.agent.model.WritingKnowledge;
import com.start.agent.repository.WritingKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 写作知识库检索服务：BM25 关键词检索 + 题材画像查询 + 写作指导。
 * 支持从 CSV 导入 9 张知识表。
 */
@Service
public class WritingKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(WritingKnowledgeService.class);

    private final WritingKnowledgeRepository repository;

    public WritingKnowledgeService(WritingKnowledgeRepository repository) {
        this.repository = repository;
    }

    /**
     * 关键词检索知识库（多表或单表）。
     */
    public List<Map<String, Object>> search(String tableName, String query) {
        List<WritingKnowledge> results;
        if (tableName != null && !tableName.isBlank()) {
            results = repository.fullTextSearch(tableName, toFullTextQuery(query), 10);
        } else {
            results = repository.fullTextSearchAll(toFullTextQuery(query), 15);
        }
        return results.stream().map(this::toMap).collect(Collectors.toList());
    }

    /**
     * 获取指定表的所有条目（用于题材画像查询）。
     */
    public List<Map<String, Object>> getTable(String tableName) {
        return repository.findByTableName(tableName).stream().map(this::toMap).collect(Collectors.toList());
    }

    /**
     * 根据题材查询写作指导。
     */
    public String getWritingGuidance(String genre) {
        if (genre == null || genre.isBlank()) return "";
        List<WritingKnowledge> guidance = repository.findByTableNameAndCategory("写作技法", genre);
        if (guidance.isEmpty()) {
            guidance = repository.findByTableName("写作技法");
        }
        if (guidance.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【RAG 写作知识检索结果】\n");
        for (int i = 0; i < Math.min(guidance.size(), 5); i++) {
            sb.append("- ").append(truncate(guidance.get(i).getContent(), 200)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 从 CSV 数据导入知识表条目。
     */
    public int importEntries(String tableName, List<Map<String, String>> rows) {
        int count = 0;
        for (Map<String, String> row : rows) {
            WritingKnowledge entry = new WritingKnowledge();
            entry.setTableName(tableName);
            entry.setContent(row.getOrDefault("content", ""));
            entry.setCategory(row.getOrDefault("category", ""));
            entry.setTags(row.getOrDefault("tags", ""));
            repository.save(entry);
            count++;
        }
        log.info("Imported {} entries into table {}", count, tableName);
        return count;
    }

    private String toFullTextQuery(String query) {
        if (query == null || query.isBlank()) return "";
        return Arrays.stream(query.split("\\s+"))
                .map(w -> "+" + w + "*")
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> toMap(WritingKnowledge wk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wk.getId());
        map.put("tableName", wk.getTableName());
        map.put("content", truncate(wk.getContent(), 300));
        map.put("category", wk.getCategory());
        map.put("tags", wk.getTags());
        return map;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
