package com.start.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.ChapterReadingPower;
import com.start.agent.repository.ChapterReadingPowerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 阅读力分类法：钩子/爽点/微兑现/硬约束违规/读者债务。
 */
@Service
public class ReadingPowerService {
    private static final Logger log = LoggerFactory.getLogger(ReadingPowerService.class);

    private final ChapterReadingPowerRepository repository;
    private final ObjectMapper objectMapper;

    public ReadingPowerService(ChapterReadingPowerRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录单章阅读力指标。
     */
    public ChapterReadingPower recordChapter(Long novelId, int chapterNumber, String hookType, String hookStrength,
                                              String coolPointPattern, List<MicroPayoff> microPayoffs,
                                              List<String> hardViolations) {
        ChapterReadingPower rp = repository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
                .orElse(new ChapterReadingPower());
        rp.setNovelId(novelId);
        rp.setChapterNumber(chapterNumber);
        rp.setHookType(hookType);
        rp.setHookStrength(hookStrength);
        rp.setCoolPointPattern(coolPointPattern);
        try {
            if (microPayoffs != null) rp.setMicroPayoffs(objectMapper.writeValueAsString(microPayoffs));
            if (hardViolations != null) rp.setHardViolations(objectMapper.writeValueAsString(hardViolations));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize reading power data", e);
        }
        return repository.save(rp);
    }

    /**
     * 获取全书阅读力趋势（用于图表）。
     */
    public List<Map<String, Object>> getChapterMetrics(Long novelId) {
        List<ChapterReadingPower> all = repository.findByNovelIdOrderByChapterNumberAsc(novelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChapterReadingPower rp : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterNumber", rp.getChapterNumber());
            item.put("hookType", rp.getHookType());
            item.put("hookStrength", rp.getHookStrength());
            item.put("coolPointPattern", rp.getCoolPointPattern());
            item.put("readerDebt", rp.getReaderDebt());
            item.put("microPayoffCount", countMicroPayoffs(rp.getMicroPayoffs()));
            item.put("hasHardViolations", rp.getHardViolations() != null && !rp.getHardViolations().isBlank() && !"[]".equals(rp.getHardViolations()));
            result.add(item);
        }
        return result;
    }

    /**
     * 硬约束检测。
     */
    public List<String> detectHardViolations(Long novelId, int chapterNumber, String chapterContent,
                                              String previousEndHook, boolean hookResolved) {
        List<String> violations = new ArrayList<>();
        if (chapterContent == null || chapterContent.isBlank()) {
            violations.add("HARD-001: 正文为空");
            return violations;
        }
        if (previousEndHook != null && !previousEndHook.isBlank() && !hookResolved) {
            violations.add("HARD-002: 上章钩子未回应（" + truncate(previousEndHook, 40) + "）");
        }
        return violations;
    }

    public record MicroPayoff(String type, String description) {}

    private int countMicroPayoffs(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return 0;
        try {
            return objectMapper.readTree(json).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
