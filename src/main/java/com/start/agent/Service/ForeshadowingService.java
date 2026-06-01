package com.start.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Foreshadowing;
import com.start.agent.model.ForeshadowingStatus;
import com.start.agent.repository.ForeshadowingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 伏笔/开放环管理：埋设 → 提醒 → 回收全生命周期追踪。
 * 支持紧急度自动升级和甘特图数据导出。
 */
@Service
public class ForeshadowingService {
    private static final Logger log = LoggerFactory.getLogger(ForeshadowingService.class);
    private static final int URGENCY_ESCALATION_CHAPTERS = 20;

    private final ForeshadowingRepository repository;
    private final ObjectMapper objectMapper;

    public ForeshadowingService(ForeshadowingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 埋设新伏笔。
     */
    public Foreshadowing plantLoop(Long novelId, int chapterNumber, String content, String loopType,
                                   String urgency, Integer deadlineChapter) {
        Foreshadowing f = new Foreshadowing();
        f.setNovelId(novelId);
        f.setContent(content);
        f.setLoopType(loopType);
        f.setUrgency(urgency != null ? urgency : "low");
        f.setStatus(ForeshadowingStatus.PLANTED.name());
        f.setPlantedChapter(chapterNumber);
        f.setDeadlineChapter(deadlineChapter);
        return repository.save(f);
    }

    /**
     * 在某章提醒已有伏笔。
     */
    public void remindLoop(Long id, int chapterNumber) {
        repository.findById(id).ifPresent(f -> {
            Set<Integer> chapters = parseRemindedChapters(f.getRemindedChapters());
            chapters.add(chapterNumber);
            try {
                f.setRemindedChapters(objectMapper.writeValueAsString(new ArrayList<>(chapters)));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize reminded chapters", e);
                return;
            }
            f.setStatus(ForeshadowingStatus.REMINDED.name());
            repository.save(f);
        });
    }

    /**
     * 回收伏笔。
     */
    public void payoffLoop(Long id, int chapterNumber) {
        repository.findById(id).ifPresent(f -> {
            f.setStatus(ForeshadowingStatus.PAID_OFF.name());
            f.setPayoffChapter(chapterNumber);
            repository.save(f);
        });
    }

    /**
     * 获取按紧急度排序的活跃伏笔（PLANTED + REMINDED），deadline 逼近的在前。
     */
    public List<Foreshadowing> getUrgentLoops(Long novelId, int topN) {
        List<Foreshadowing> all = repository.findUrgentLoops(novelId,
                List.of(ForeshadowingStatus.PLANTED.name(), ForeshadowingStatus.REMINDED.name()));
        if (all.size() <= topN) return all;
        return all.subList(0, topN);
    }

    /**
     * 甘特图数据：每条伏笔的埋设章→回收章区间，按紧急度着色。
     */
    public List<Map<String, Object>> getGanttData(Long novelId) {
        List<Foreshadowing> all = repository.findByNovelIdOrderByPlantedChapterAsc(novelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Foreshadowing f : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("content", truncate(f.getContent(), 80));
            item.put("loopType", f.getLoopType());
            item.put("urgency", f.getUrgency());
            item.put("status", f.getStatus());
            item.put("plantedChapter", f.getPlantedChapter());
            item.put("payoffChapter", f.getPayoffChapter());
            item.put("deadlineChapter", f.getDeadlineChapter());
            result.add(item);
        }
        return result;
    }

    /**
     * 自动升级紧急度：已埋设超过阈值的伏笔自动升级。
     */
    public void escalateUrgency(Long novelId, int currentChapter) {
        List<Foreshadowing> active = repository.findByNovelIdAndStatusNot(novelId, ForeshadowingStatus.PAID_OFF.name());
        for (Foreshadowing f : active) {
            int age = currentChapter - f.getPlantedChapter();
            String newUrgency;
            if (age > URGENCY_ESCALATION_CHAPTERS * 2) newUrgency = "critical";
            else if (age > URGENCY_ESCALATION_CHAPTERS) newUrgency = "high";
            else if (age > URGENCY_ESCALATION_CHAPTERS / 2) newUrgency = "medium";
            else continue;
            if (!newUrgency.equals(f.getUrgency())) {
                f.setUrgency(newUrgency);
                repository.save(f);
            }
        }
    }

    private Set<Integer> parseRemindedChapters(String json) {
        if (json == null || json.isBlank()) return new LinkedHashSet<>();
        try {
            @SuppressWarnings("unchecked")
            List<Integer> list = objectMapper.readValue(json, List.class);
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
