package com.start.agent.service;

import com.start.agent.model.Novel;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 写前上下文 Agent：research → 五段写作任务书。
 * 数据权重：用户要求 > 章纲 > 契约 > reasoning > COMMIT > CSV 检索。
 */
@Service
public class ContextAgentService {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ContextAgentService.class);

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final StoryContractService contractService;
    private final StrandWeaveService strandWeaveService;
    private final StructuredOutlineService structuredOutlineService;
    private final ForeshadowingService foreshadowingService;

    public ContextAgentService(NovelRepository novelRepository, ChapterRepository chapterRepository,
                                CharacterProfileRepository characterProfileRepository,
                                StoryContractService contractService, StrandWeaveService strandWeaveService,
                                StructuredOutlineService structuredOutlineService,
                                ForeshadowingService foreshadowingService) {
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.contractService = contractService;
        this.strandWeaveService = strandWeaveService;
        this.structuredOutlineService = structuredOutlineService;
        this.foreshadowingService = foreshadowingService;
    }

    /**
     * 生成五段写作任务书。
     */
    public String buildWritingBrief(Long novelId, int chapterNumber) {
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) return "";

        // 收集数据
        Map<String, Object> directive = contractService.getChapterDirective(novelId, chapterNumber);
        String structuredBlock = structuredOutlineService.buildChapterDirectiveBlock(novelId, chapterNumber);
        List<com.start.agent.model.Foreshadowing> urgentLoops = foreshadowingService.getUrgentLoops(novelId, 3);

        String prevContent = getPreviousChapterContent(novelId, chapterNumber - 1);
        String nextChapterGoal = getNextChapterOutline(novelId, chapterNumber);

        StringBuilder brief = new StringBuilder();

        // === 1. 开篇委托 ===
        brief.append("你现在要写《").append(novel.getTitle()).append("》第").append(chapterNumber).append("章。\n\n");

        // === 2. 这章的故事 ===
        brief.append("【本章故事】\n");
        String goal = (String) directive.getOrDefault("goal", nextChapterGoal);
        if (!goal.isBlank()) {
            brief.append("本章目标：").append(goal).append("\n");
        }
        if (!structuredBlock.isEmpty()) {
            brief.append(structuredBlock).append("\n");
        }

        @SuppressWarnings("unchecked")
        List<String> mustCover = (List<String>) directive.getOrDefault("mustCoverNodes", List.of());
        @SuppressWarnings("unchecked")
        List<String> forbidden = (List<String>) directive.getOrDefault("forbiddenZones", List.of());
        if (!mustCover.isEmpty()) {
            brief.append("必须覆盖：");
            brief.append(String.join("、", mustCover)).append("\n");
        }
        if (!forbidden.isEmpty()) {
            brief.append("本章禁区：");
            brief.append(String.join("、", forbidden)).append("\n");
        }

        if (prevContent != null && !prevContent.isEmpty()) {
            brief.append("前文承接：上章结尾，").append(truncate(prevContent, 200)).append("\n");
        }

        // 伏笔提醒
        if (!urgentLoops.isEmpty()) {
            brief.append("活跃伏笔（请推进或回收）：\n");
            for (var loop : urgentLoops) {
                brief.append("- [").append(loop.getUrgency()).append("] Ch").append(loop.getPlantedChapter())
                     .append(": ").append(truncate(loop.getContent(), 60)).append("\n");
            }
        }

        // === 3. 这章的人物 ===
        brief.append("\n【本章人物】\n");
        var profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        if (profiles.isEmpty()) {
            brief.append("（无已记录角色设定，请基于大纲创作）\n");
        } else {
            for (var profile : profiles) {
                if (profile.getCharacterName() == null || profile.getCharacterName().isBlank()) continue;
                brief.append("- ").append(profile.getCharacterName());
                String type = profile.getCharacterType();
                if (type != null && !type.isBlank()) {
                    brief.append("（").append(type).append("）");
                }
                brief.append(": ").append(truncate(profile.getProfileContent(), 100)).append("\n");
            }
        }

        // === 4. 怎么写更顺 ===
        brief.append("\n【怎么写更顺】\n");
        String pipeline = novel.getWritingPipeline();
        brief.append("风格: ").append(pipeline != null ? pipeline : "爽文").append("\n");
        brief.append("题材: ").append(novel.getTopic()).append("\n");

        // Strand 约束
        brief.append(strandWeaveService.buildStrandPromptBlock(novelId, chapterNumber)).append("\n");

        // Anti-AI 硬规则
        brief.append("""
                【反AI味硬规则】
                1. 删段末感悟句，留余味——不写成四段闭环
                2. 删万能副词（缓缓/淡淡/微微），换具体动作
                3. 情绪用生理反应+微动作，禁止"他感到X"
                4. 对话带潜台词和意图冲突，有抢话、沉默、答非所问
                5. 制造节奏疏密对比，有的段落只一句话
                6. 章末禁止安全着陆，留未解决的问题
                7. 展示后不解释
                """);

        // === 5. 收在哪里 ===
        brief.append("\n【收在哪里】\n");
        brief.append("章末留一个具体的未闭合问题。读者合上这章时应该带着[接下来会怎样]的念头。\n");
        brief.append("结尾停在动作/表情/细节上，不在句号处完结情绪。\n");

        return brief.toString().trim();
    }

    private String getPreviousChapterContent(Long novelId, int chapterNumber) {
        return chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
                .map(c -> {
                    String content = c.getContent();
                    if (content == null || content.isBlank()) return "";
                    return content.length() > 500 ? content.substring(content.length() - 500) : content;
                }).orElse("");
    }

    private String getNextChapterOutline(Long novelId, int chapterNumber) {
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null || novel.getDescription() == null) return "";
        String outline = novel.getDescription();
        String marker = "第" + chapterNumber + "章";
        int idx = outline.indexOf(marker);
        if (idx < 0) {
            marker = "第" + chapterNumber + "章";
            idx = outline.indexOf(marker);
        }
        if (idx < 0) return "";
        int end = outline.indexOf("\n", idx + marker.length());
        if (end < 0) end = Math.min(outline.length(), idx + 200);
        return outline.substring(idx, end).trim();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
