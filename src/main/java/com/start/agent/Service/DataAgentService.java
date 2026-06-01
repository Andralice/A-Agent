package com.start.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.CharacterProfile;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 写后数据 Agent：从章节正文提取结构化事实，生成 fulfillment/extraction/state_deltas。
 * 替代旧的分散式侧车提取。
 */
@Service
public class DataAgentService {
    private static final Logger log = LoggerFactory.getLogger(DataAgentService.class);

    private final ChapterRepository chapterRepository;
    private final ChapterFactRepository factRepository;
    private final ForeshadowingService foreshadowingService;
    private final ReadingPowerService readingPowerService;
    private final ObjectMapper objectMapper;

    public DataAgentService(ChapterRepository chapterRepository, ChapterFactRepository factRepository,
                             ForeshadowingService foreshadowingService, ReadingPowerService readingPowerService,
                             ObjectMapper objectMapper) {
        this.chapterRepository = chapterRepository;
        this.factRepository = factRepository;
        this.foreshadowingService = foreshadowingService;
        this.readingPowerService = readingPowerService;
        this.objectMapper = objectMapper;
    }

    /**
     * 从章节正文提取所有结构化数据。
     */
    public DataExtractionResult extractChapterFacts(Long novelId, int chapterNumber) {
        Chapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber).orElse(null);
        if (chapter == null || chapter.getContent() == null) {
            return DataExtractionResult.empty(chapterNumber);
        }

        String content = chapter.getContent();

        // 1. 提取实体（人名、地名）
        List<EntityAppearance> entities = extractEntities(content);

        // 2. 提取场景
        List<SceneInfo> scenes = extractScenes(content);

        // 3. 生成摘要
        String summary = generateSummary(content, chapter.getTitle());

        // 4. 提取伏笔
        List<Map<String, Object>> newLoops = extractForeshadowing(content, novelId, chapterNumber);

        // 5. 更新 ChapterFact
        saveFacts(novelId, chapterNumber, entities);

        // 6. 判断主导 Strand
        String dominantStrand = inferDominantStrand(content);
        chapter.setDominantStrand(dominantStrand);
        chapterRepository.save(chapter);

        // 7. 构建 node fulfillment
        Map<String, Object> fulfillment = buildFulfillmentResult(novelId, chapterNumber);

        // 8. 构建 extraction result
        Map<String, Object> extraction = buildExtractionResult(entities, scenes, summary, dominantStrand, newLoops);

        return new DataExtractionResult(chapterNumber, fulfillment, extraction, entities, scenes,
                summary, dominantStrand, newLoops);
    }

    private List<EntityAppearance> extractEntities(String content) {
        List<EntityAppearance> result = new ArrayList<>();
        // 简单的中文人名提取（2-4 字汉名）
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
                "(?<![\\p{IsHan}])([\\p{IsHan}]{2,4})(?![\\p{IsHan}])");
        java.util.regex.Matcher m = namePattern.matcher(content);
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        while (m.find()) {
            String name = m.group(1);
            if (name.matches(".*[的了么呢吧吗啊呀喔哦哈嗯哎嗨].*")) continue;
            if (name.startsWith("第") || name.endsWith("章节") || name.contains("本章") || name.contains("未完")) continue;
            if (name.contains("那") || name.contains("这") || name.contains("一") || name.contains("些")) continue;
            counts.merge(name, 1, Integer::sum);
        }
        // 只取出现 >= 2 次的名字
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 2 && seen.add(entry.getKey())) {
                result.add(new EntityAppearance(entry.getKey(), "角色", entry.getValue(), 0.7));
            }
        }
        return result;
    }

    private List<SceneInfo> extractScenes(String content) {
        // 简单按段落切分场景
        List<SceneInfo> scenes = new ArrayList<>();
        String[] paragraphs = content.split("\n\n");
        int lineStart = 1;
        for (int i = 0; i < paragraphs.length && scenes.size() < 6; i++) {
            String p = paragraphs[i].trim();
            if (p.length() < 50) continue;
            int lineEnd = lineStart + p.split("\n").length;
            scenes.add(new SceneInfo(i + 1, lineStart, lineEnd, "未知地点",
                    truncate(p, 80), List.of()));
            lineStart = lineEnd + 2;
        }
        return scenes;
    }

    private String generateSummary(String content, String title) {
        // 取正文前 150 字作为简单摘要
        String firstPart = content.length() > 150 ? content.substring(0, 150) : content;
        return truncate(firstPart.replace("\n", " "), 150);
    }

    private List<Map<String, Object>> extractForeshadowing(String content, Long novelId, int chapterNumber) {
        List<Map<String, Object>> loops = new ArrayList<>();
        // 检测常见伏笔埋设关键词
        String[] foreshadowMarkers = {"就在这时", "他没注意到", "远处", "隐约", "似乎", "仿佛", "突然", "忽然"};
        for (String marker : foreshadowMarkers) {
            int idx = content.indexOf(marker);
            if (idx >= 0) {
                int start = Math.max(0, idx - 20);
                int end = Math.min(content.length(), idx + 80);
                String snippet = content.substring(start, end).trim();
                if (snippet.length() > 30 && mightBeForeshadowing(snippet)) {
                    // 自动埋设
                    var f = foreshadowingService.plantLoop(novelId, chapterNumber, snippet,
                            "MYSTERY", "low", null);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", f.getId());
                    item.put("content", snippet);
                    item.put("type", "open_loop_created");
                    loops.add(item);
                    break; // 每章最多自动埋设 1 条
                }
            }
        }
        return loops;
    }

    private boolean mightBeForeshadowing(String snippet) {
        return snippet.contains("不知") || snippet.contains("并未") || snippet.contains("似乎") ||
               snippet.contains("隐约") || snippet.contains("藏") || snippet.contains("秘密") ||
               snippet.contains("奇怪") || snippet.contains("异常");
    }

    private void saveFacts(Long novelId, int chapterNumber, List<EntityAppearance> entities) {
        for (EntityAppearance e : entities) {
            ChapterFact fact = new ChapterFact();
            fact.setNovelId(novelId);
            fact.setChapterNumber(chapterNumber);
            fact.setFactType("sidecar_fact");
            fact.setSubjectName(e.name);
            fact.setFactContent("第" + chapterNumber + "章出场，" + e.mentionCount + "次");
            factRepository.save(fact);
        }
    }

    private String inferDominantStrand(String content) {
        int questScore = 0, fireScore = 0, constellationScore = 0;
        // 简化版：关键词计数
        String[] questKeywords = {"战斗", "修炼", "突破", "境界", "击杀", "功法", "丹药", "挑战", "试炼", "夺宝"};
        String[] fireKeywords = {"感情", "心动", "牵手", "拥抱", "喜欢", "爱", "情", "暧昧", "甜蜜", "温柔"};
        String[] constellationKeywords = {"世界", "势力", "宗门", "圣地", "禁地", "传说", "秘密", "历史", "规则", "真相"};

        for (String kw : questKeywords) questScore += countOccurrences(content, kw);
        for (String kw : fireKeywords) fireScore += countOccurrences(content, kw);
        for (String kw : constellationKeywords) constellationScore += countOccurrences(content, kw);

        if (questScore >= fireScore && questScore >= constellationScore) return "QUEST";
        if (fireScore >= constellationScore) return "FIRE";
        return "CONSTELLATION";
    }

    private Map<String, Object> buildFulfillmentResult(Long novelId, int chapterNumber) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planned_nodes", List.of());
        result.put("covered_nodes", List.of());
        result.put("missed_nodes", List.of());
        result.put("extra_nodes", List.of());
        return result;
    }

    private Map<String, Object> buildExtractionResult(List<EntityAppearance> entities, List<SceneInfo> scenes,
                                                       String summary, String dominantStrand,
                                                       List<Map<String, Object>> newLoops) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> entList = new ArrayList<>();
        for (EntityAppearance e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", e.name);
            item.put("type", e.type);
            item.put("mentionCount", e.mentionCount);
            item.put("confidence", e.confidence);
            entList.add(item);
        }
        result.put("entities_appeared", entList);
        result.put("scenes", scenes);
        result.put("summary_text", summary);
        result.put("dominant_strand", dominantStrand);
        result.put("accepted_events", newLoops);
        result.put("state_deltas", List.of());
        result.put("entity_deltas", List.of());
        return result;
    }

    private int countOccurrences(String content, String phrase) {
        int count = 0, idx = 0;
        while ((idx = content.indexOf(phrase, idx)) >= 0) { count++; idx += phrase.length(); }
        return count;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    // ── DTOs ──

    public record DataExtractionResult(int chapterNumber, Map<String, Object> fulfillmentResult,
                                        Map<String, Object> extractionResult, List<EntityAppearance> entities,
                                        List<SceneInfo> scenes, String summary, String dominantStrand,
                                        List<Map<String, Object>> newLoops) {
        public static DataExtractionResult empty(int chapterNumber) {
            return new DataExtractionResult(chapterNumber, Map.of(), Map.of(), List.of(),
                    List.of(), "", "QUEST", List.of());
        }
    }

    public record EntityAppearance(String name, String type, int mentionCount, double confidence) {}

    public record SceneInfo(int index, int startLine, int endLine, String location, String summary,
                             List<String> characters) {}
}
