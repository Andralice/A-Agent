package com.start.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Novel;
import com.start.agent.model.StoryStrand;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三股线节奏控制：QUEST（主线55-65%）/ FIRE（感情20-30%）/ CONSTELLATION（世界观10-20%）。
 * 硬约束：Quest 不连续超过5章，Fire 不超过10章不出现，Constellation 不超过15章不出现。
 */
@Service
public class StrandWeaveService {
    private static final Logger log = LoggerFactory.getLogger(StrandWeaveService.class);
    private static final int QUEST_MAX_CONSECUTIVE = 5;
    private static final int FIRE_MAX_GAP = 10;
    private static final int CONSTELLATION_MAX_GAP = 15;

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final ObjectMapper objectMapper;

    public StrandWeaveService(NovelRepository novelRepository, ChapterRepository chapterRepository, ObjectMapper objectMapper) {
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.objectMapper = objectMapper;
    }

    public record StrandStats(long questCount, long fireCount, long constellationCount,
                              int lastQuestChapter, int lastFireChapter, int lastConstellationChapter,
                              StoryStrand currentDominant, int chaptersSinceSwitch,
                              String suggestion, boolean warning) {}

    /**
     * 为当前章节分配主导 Strand，并返回 Stats 含建议。
     */
    public StrandStats assignStrand(Long novelId, int chapterNumber) {
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) return defaultStats();

        StrandTracker tracker = loadTracker(novel);

        long questCount = countStrand(novelId, "QUEST");
        long fireCount = countStrand(novelId, "FIRE");
        long constellationCount = countStrand(novelId, "CONSTELLATION");

        int chaptersSinceFire = chapterNumber - tracker.lastFireChapter;
        int chaptersSinceConstellation = chapterNumber - tracker.lastConstellationChapter;

        StoryStrand suggested;
        boolean warning = false;
        StringBuilder suggestion = new StringBuilder();

        if (tracker.currentDominant != null && "QUEST".equals(tracker.currentDominant.name())
                && tracker.chaptersSinceSwitch >= QUEST_MAX_CONSECUTIVE) {
            suggested = chaptersSinceFire >= FIRE_MAX_GAP ? StoryStrand.FIRE : StoryStrand.CONSTELLATION;
            warning = true;
            suggestion.append("Quest连续").append(tracker.chaptersSinceSwitch).append("章已达上限，建议切换到")
                     .append(suggested == StoryStrand.FIRE ? "感情线" : "世界观线").append("。");
        } else if (chaptersSinceFire >= FIRE_MAX_GAP) {
            suggested = StoryStrand.FIRE;
            warning = true;
            suggestion.append("感情线已").append(chaptersSinceFire).append("章未出现，必须安排。");
        } else if (chaptersSinceConstellation >= CONSTELLATION_MAX_GAP) {
            suggested = StoryStrand.CONSTELLATION;
            warning = true;
            suggestion.append("世界观线已").append(chaptersSinceConstellation).append("章未出现，建议展示新设定。");
        } else {
            suggested = tracker.currentDominant != null ? tracker.currentDominant : StoryStrand.QUEST;
        }

        if (suggestion.isEmpty()) {
            long total = questCount + fireCount + constellationCount + 1;
            double fireRatio = (double) (fireCount + (suggested == StoryStrand.FIRE ? 1 : 0)) / total;
            double constellationRatio = (double) (constellationCount + (suggested == StoryStrand.CONSTELLATION ? 1 : 0)) / total;
            if (fireRatio < 0.15 && suggested != StoryStrand.FIRE) {
                suggestion.append("感情线占比偏低(").append(String.format("%.0f%%", fireRatio * 100)).append(")，建议近期安排。");
            } else if (constellationRatio < 0.08 && suggested != StoryStrand.CONSTELLATION) {
                suggestion.append("世界观线占比偏低(").append(String.format("%.0f%%", constellationRatio * 100)).append(")，建议近期展示。");
            }
        }

        int newChaptersSinceSwitch = (tracker.currentDominant == suggested) ? tracker.chaptersSinceSwitch + 1 : 1;
        updateTracker(novel, suggested, chapterNumber, newChaptersSinceSwitch);

        return new StrandStats(questCount, fireCount, constellationCount,
                tracker.lastQuestChapter, tracker.lastFireChapter, tracker.lastConstellationChapter,
                suggested, newChaptersSinceSwitch, suggestion.toString(), warning);
    }

    public StrandStats getStrandStats(Long novelId) {
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) return defaultStats();
        StrandTracker tracker = loadTracker(novel);
        long questCount = countStrand(novelId, "QUEST");
        long fireCount = countStrand(novelId, "FIRE");
        long constellationCount = countStrand(novelId, "CONSTELLATION");
        return new StrandStats(questCount, fireCount, constellationCount,
                tracker.lastQuestChapter, tracker.lastFireChapter, tracker.lastConstellationChapter,
                tracker.currentDominant, tracker.chaptersSinceSwitch, "", false);
    }

    public String buildStrandPromptBlock(Long novelId, int chapterNumber) {
        StrandStats stats = getStrandStats(novelId);
        StringBuilder block = new StringBuilder();
        block.append("【三股线节奏约束】\n");
        block.append("- 本章主导线：").append(label(stats.currentDominant)).append("\n");
        long total = stats.questCount + stats.fireCount + stats.constellationCount + 1;
        block.append("- 当前占比：主线").append(String.format("%.0f%%", stats.questCount * 100.0 / total))
             .append(" / 感情").append(String.format("%.0f%%", stats.fireCount * 100.0 / total))
             .append(" / 世界观").append(String.format("%.0f%%", stats.constellationCount * 100.0 / total)).append("\n");
        block.append("- 硬约束：主线不连续超5章 | 感情不超10章不出现 | 世界观不超15章不出现\n");
        if (!stats.suggestion.isEmpty()) {
            block.append("- 提示：").append(stats.suggestion).append("\n");
        }
        return block.toString().trim();
    }

    private long countStrand(Long novelId, String strand) {
        return chapterRepository.countByNovelIdAndDominantStrand(novelId, strand);
    }

    private StrandTracker loadTracker(Novel novel) {
        StrandTracker tracker = new StrandTracker();
        if (novel.getStrandTrackerJson() != null && !novel.getStrandTrackerJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(novel.getStrandTrackerJson(), Map.class);
                tracker.lastQuestChapter = toInt(map.get("lastQuestChapter"));
                tracker.lastFireChapter = toInt(map.get("lastFireChapter"));
                tracker.lastConstellationChapter = toInt(map.get("lastConstellationChapter"));
                String dom = (String) map.get("currentDominant");
                tracker.currentDominant = dom != null ? StoryStrand.valueOf(dom) : null;
                tracker.chaptersSinceSwitch = toInt(map.get("chaptersSinceSwitch"));
            } catch (Exception e) {
                log.debug("Failed to parse strand tracker, using defaults: {}", e.getMessage());
            }
        }
        return tracker;
    }

    private void updateTracker(Novel novel, StoryStrand dominant, int chapterNumber, int chaptersSinceSwitch) {
        StrandTracker tracker = loadTracker(novel);
        switch (dominant) {
            case QUEST -> tracker.lastQuestChapter = chapterNumber;
            case FIRE -> tracker.lastFireChapter = chapterNumber;
            case CONSTELLATION -> tracker.lastConstellationChapter = chapterNumber;
        }
        tracker.currentDominant = dominant;
        tracker.chaptersSinceSwitch = chaptersSinceSwitch;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lastQuestChapter", tracker.lastQuestChapter);
        map.put("lastFireChapter", tracker.lastFireChapter);
        map.put("lastConstellationChapter", tracker.lastConstellationChapter);
        map.put("currentDominant", tracker.currentDominant != null ? tracker.currentDominant.name() : null);
        map.put("chaptersSinceSwitch", tracker.chaptersSinceSwitch);
        try {
            novel.setStrandTrackerJson(objectMapper.writeValueAsString(map));
            novelRepository.save(novel);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize strand tracker", e);
        }
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private String label(StoryStrand s) {
        if (s == null) return "主线";
        return switch (s) {
            case QUEST -> "主线(Quest)";
            case FIRE -> "感情线(Fire)";
            case CONSTELLATION -> "世界观线(Constellation)";
        };
    }

    private StrandStats defaultStats() {
        return new StrandStats(0, 0, 0, 0, 0, 0, StoryStrand.QUEST, 1, "", false);
    }

    private static class StrandTracker {
        int lastQuestChapter, lastFireChapter, lastConstellationChapter;
        StoryStrand currentDominant;
        int chaptersSinceSwitch = 1;
    }
}
