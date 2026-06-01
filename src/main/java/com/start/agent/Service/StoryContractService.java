package com.start.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.Novel;
import com.start.agent.model.StoryContract;
import com.start.agent.model.StoryContractStatus;
import com.start.agent.model.StoryContractType;
import com.start.agent.repository.StoryContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 故事契约服务：管理 MASTER_SETTING → VOLUME → CHAPTER → REVIEW → CHAPTER_COMMIT 主线真源链路。
 * 只做增量写入，不重写整份设定。
 */
@Service
public class StoryContractService {
    private static final Logger log = LoggerFactory.getLogger(StoryContractService.class);

    private final StoryContractRepository repository;
    private final ObjectMapper objectMapper;

    public StoryContractService(StoryContractRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入或替换指定类型的活跃契约（旧 ACTIVE → SUPERSEDED，新 DRAFT → ACTIVE）。
     */
    public StoryContract upsertContract(Long novelId, StoryContractType type, Integer volumeNumber,
                                        Integer chapterNumber, Map<String, Object> content) {
        repository.supersedeActive(novelId, type.name());
        StoryContract contract = new StoryContract();
        contract.setNovelId(novelId);
        contract.setContractType(type.name());
        contract.setVolumeNumber(volumeNumber);
        contract.setChapterNumber(chapterNumber);
        try {
            contract.setContentJson(objectMapper.writeValueAsString(content));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize contract content", e);
            contract.setContentJson("{}");
        }
        contract.setStatus(StoryContractStatus.ACTIVE.name());
        return repository.save(contract);
    }

    /**
     * 创建 MASTER_SETTING 契约。
     */
    public StoryContract createMasterSetting(Long novelId, Novel novel) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", novel.getTitle());
        content.put("topic", novel.getTopic());
        content.put("pipeline", novel.getWritingPipeline());
        content.put("genre", novel.getTopic());
        content.put("coreConstraints", List.of(
                "大纲即法律——生成正文时必须遵循大纲结构",
                "设定即物理——角色能力不得超过已有记录",
                "新角色/新设定必须在章节落库后由数据提取入库"
        ));
        return upsertContract(novelId, StoryContractType.MASTER_SETTING, null, null, content);
    }

    /**
     * 创建 CHAPTER 契约（含 CBN/CPNs/CEN）。
     */
    public StoryContract createChapterContract(Long novelId, int volumeNumber, int chapterNumber,
                                                String chapterGoal, List<String> mustCoverNodes,
                                                List<String> forbiddenZones) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("chapterNumber", chapterNumber);
        content.put("goal", chapterGoal);
        content.put("mustCoverNodes", mustCoverNodes != null ? mustCoverNodes : List.of());
        content.put("forbiddenZones", forbiddenZones != null ? forbiddenZones : List.of());
        return upsertContract(novelId, StoryContractType.CHAPTER, volumeNumber, chapterNumber, content);
    }

    /**
     * 获取小说的完整契约树。
     */
    public Map<String, Object> getContractTree(Long novelId) {
        List<StoryContract> all = repository.findByNovelIdOrderByCreateTimeAsc(novelId);
        Map<String, Object> tree = new LinkedHashMap<>();
        List<Map<String, Object>> masters = new ArrayList<>();
        List<Map<String, Object>> volumes = new ArrayList<>();
        List<Map<String, Object>> chapters = new ArrayList<>();
        List<Map<String, Object>> reviews = new ArrayList<>();
        List<Map<String, Object>> commits = new ArrayList<>();

        for (StoryContract sc : all) {
            Map<String, Object> item = contractToMap(sc);
            switch (sc.getContractType()) {
                case "MASTER_SETTING" -> masters.add(item);
                case "VOLUME" -> volumes.add(item);
                case "CHAPTER" -> chapters.add(item);
                case "REVIEW" -> reviews.add(item);
                case "CHAPTER_COMMIT" -> commits.add(item);
            }
        }
        tree.put("masterSettings", masters);
        tree.put("volumes", volumes);
        tree.put("chapters", chapters);
        tree.put("reviews", reviews);
        tree.put("commits", commits);
        return tree;
    }

    /**
     * 获取指定章节的契约指令（用于写前上下文组装）。
     */
    public Map<String, Object> getChapterDirective(Long novelId, int chapterNumber) {
        List<StoryContract> contracts = repository.findByNovelIdAndContractTypeAndChapterNumber(
                novelId, StoryContractType.CHAPTER.name(), chapterNumber);
        if (contracts.isEmpty()) return Collections.emptyMap();
        StoryContract sc = contracts.get(contracts.size() - 1); // 最新一条
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(sc.getContentJson(), Map.class);
            return result;
        } catch (Exception e) {
            log.error("Failed to parse chapter contract", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> contractToMap(StoryContract sc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", sc.getId());
        item.put("type", sc.getContractType());
        item.put("volumeNumber", sc.getVolumeNumber());
        item.put("chapterNumber", sc.getChapterNumber());
        item.put("status", sc.getStatus());
        try {
            item.put("content", sc.getContentJson() != null ? objectMapper.readValue(sc.getContentJson(), Map.class) : Map.of());
        } catch (Exception e) {
            item.put("content", Map.of());
        }
        return item;
    }
}
