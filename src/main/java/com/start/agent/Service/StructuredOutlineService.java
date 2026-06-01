package com.start.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Novel;
import com.start.agent.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 结构化大纲服务：解析 CBN/CPN/CEN 节点，为写前上下文组装提供章指令。
 */
@Service
public class StructuredOutlineService {
    private static final Logger log = LoggerFactory.getLogger(StructuredOutlineService.class);

    private final NovelRepository novelRepository;
    private final ObjectMapper objectMapper;

    public StructuredOutlineService(NovelRepository novelRepository, ObjectMapper objectMapper) {
        this.novelRepository = novelRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 单章结构化节点 DTO。
     */
    public record ChapterOutlineNode(int chapterNumber, String cbn, List<String> cpns, String cen,
                                      List<String> mustCoverNodes, List<String> forbiddenZones) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("chapterNumber", chapterNumber);
            map.put("cbn", cbn);
            map.put("cpns", cpns);
            map.put("cen", cen);
            map.put("mustCoverNodes", mustCoverNodes);
            map.put("forbiddenZones", forbiddenZones);
            return map;
        }
    }

    /**
     * 解析全书结构化大纲。
     */
    public List<ChapterOutlineNode> parseStructuredOutline(Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null || novel.getStructuredOutlineJson() == null || novel.getStructuredOutlineJson().isBlank()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = objectMapper.readValue(novel.getStructuredOutlineJson(), List.class);
            List<ChapterOutlineNode> nodes = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                int chNum = toInt(item.get("chapterNumber"));
                String cbn = (String) item.getOrDefault("cbn", "");
                @SuppressWarnings("unchecked")
                List<String> cpns = (List<String>) item.getOrDefault("cpns", List.of());
                String cen = (String) item.getOrDefault("cen", "");
                @SuppressWarnings("unchecked")
                List<String> mcns = (List<String>) item.getOrDefault("mustCoverNodes", List.of());
                @SuppressWarnings("unchecked")
                List<String> fzs = (List<String>) item.getOrDefault("forbiddenZones", List.of());
                nodes.add(new ChapterOutlineNode(chNum, cbn, cpns, cen, mcns, fzs));
            }
            return nodes;
        } catch (Exception e) {
            log.error("Failed to parse structured outline", e);
            return List.of();
        }
    }

    /**
     * 为特定章构建「章指令」prompt block。
     */
    public String buildChapterDirectiveBlock(Long novelId, int chapterNumber) {
        List<ChapterOutlineNode> all = parseStructuredOutline(novelId);
        ChapterOutlineNode node = all.stream()
                .filter(n -> n.chapterNumber == chapterNumber)
                .findFirst().orElse(null);
        if (node == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【本章结构化指令】\n");
        sb.append("- 章节起点(CBN): ").append(emptyToNA(node.cbn)).append("\n");
        sb.append("- 推进节点(CPNs):\n");
        for (int i = 0; i < node.cpns.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(node.cpns.get(i)).append("\n");
        }
        sb.append("- 章节终点(CEN): ").append(emptyToNA(node.cen)).append("\n");
        if (!node.mustCoverNodes.isEmpty()) {
            sb.append("- 必须覆盖:\n");
            for (String n : node.mustCoverNodes) {
                sb.append("  • ").append(n).append("\n");
            }
        }
        if (!node.forbiddenZones.isEmpty()) {
            sb.append("- 本章禁区:\n");
            for (String z : node.forbiddenZones) {
                sb.append("  ✗ ").append(z).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 生成示意 LLM 的结构化大纲输出格式 prompt。
     */
    public String structuredOutlineFormatPrompt() {
        return """
                【结构化大纲格式要求】
                请在生成大纲时，为每一章提供以下结构化节点（JSON 数组）：
                ```json
                [
                  {
                    "chapterNumber": 1,
                    "cbn": "主角 | 抵达 | 新手村",
                    "cpns": ["主角 | 接受 | 第一个任务", "主角 | 击败 | 初级怪物"],
                    "cen": "主角 | 获得 | 第一件装备",
                    "mustCoverNodes": ["展示世界基本规则", "引入第一个冲突"],
                    "forbiddenZones": ["主角不能在本章就展示全部实力"]
                  }
                ]
                ```
                节点格式：主体 | 动作/变化 | 对象/结果
                每章固定 1 个 CBN + 2~4 个 CPN + 1 个 CEN
                必须覆盖节点 ≤ 4 个，本章禁区 ≤ 5 条
                """;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private String emptyToNA(String s) {
        return (s == null || s.isBlank()) ? "（未指定）" : s;
    }
}
