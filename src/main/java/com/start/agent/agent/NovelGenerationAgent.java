package com.start.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.start.agent.exception.ChapterGenerationAbortedException;
import com.start.agent.model.WritingPipeline;
import com.start.agent.model.WritingStyleHints;
import com.start.agent.narrative.CognitionArcResolver;
import com.start.agent.narrative.CognitionArcSnapshot;
import com.start.agent.narrative.ProseCraftResolver;
import com.start.agent.narrative.ProseCraftSnapshot;
import com.start.agent.narrative.NarrativeCriticReport;
import com.start.agent.narrative.NarrativeEngineArtifactSink;
import com.start.agent.narrative.NarrativeLint;
import com.start.agent.narrative.NarrativeLintReport;
import com.start.agent.narrative.NarrativePhysicsMode;
import com.start.agent.narrative.NarrativeProfile;
import com.start.agent.narrative.NarrativeProfileResolver;
import com.start.agent.narrative.OutlineCharacterExcerpt;
import com.start.agent.prompt.NarrativeCraftPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 小说章节生成编排：串联初稿、一致性审查、内容审核、润色、终稿去 AI 味；并按 {@link WritingPipeline}、热梗开关拼接提示词。
 */
@Slf4j
@Component
public class NovelGenerationAgent {

    private static final int LN_PLAN_OUTLINE_CLIP = 10_000;
    private static final int LN_PLAN_PROFILE_CLIP = 3_500;
    private static final int LN_PLAN_PREV_CLIP = 6_000;
    private static final int LN_PLAN_IMMUTABLE_CLIP = 2_500;
    private static final int NE_PLAN_OUTLINE_CLIP = 9_000;
    private static final int NE_PLAN_PROFILE_CLIP = 3_200;
    private static final int NE_PLAN_PREV_CLIP = 5_500;
    private static final int NE_PLAN_IMMUTABLE_CLIP = 2_400;

    private final ChatClient chatClient;
    private final ContentReviewAgent reviewAgent;
    private final PolishingAgent polishingAgent;
    private final ConsistencyReviewAgent consistencyAgent;
    private final ObjectMapper objectMapper;
    private final NarrativeProfileResolver narrativeProfileResolver;
    private final CognitionArcResolver cognitionArcResolver;
    private final ProseCraftResolver proseCraftResolver;

    /** 轻小说章前节拍规划（多一次模型调用）；可通过配置关闭。 */
    @Value("${novel.light-novel.chapter-planning-enabled:true}")
    private boolean lightNovelChapterPlanningEnabled;

    /** 叙事引擎 1.0：章节情绪带宽与语言力学约束（初稿/润色/终稿注入）。 */
    @Value("${novel.narrative-engine.enabled:true}")
    private boolean narrativeEngineEnabled;

    /** M2：章前叙事 Planner（多一次模型调用）；失败或非适用管线时静默跳过。 */
    @Value("${novel.narrative-engine.two-phase-enabled:false}")
    private boolean narrativeTwoPhaseEnabled;

    /**
     * 轻小说已启用「章拍规划」时默认跳过叙事 Planner，避免两套节拍冲突（可配置关闭该跳过）。
     */
    @Value("${novel.narrative-engine.two-phase-skip-with-light-novel-plan:true}")
    private boolean narrativeTwoPhaseSkipWithLightNovelPlan;

    @Value("${novel.narrative-engine.planner-temperature:0.35}")
    private double narrativePlannerTemperature;

    @Value("${novel.narrative-engine.planner-max-tokens:900}")
    private int narrativePlannerMaxTokens;

    /** M3：终稿后 Lint；命中时可窄幅修订（多一次模型调用）。 */
    @Value("${novel.narrative-engine.lint-enabled:true}")
    private boolean narrativeLintEnabled;

    @Value("${novel.narrative-engine.lint-fix-enabled:true}")
    private boolean narrativeLintFixEnabled;

    @Value("${novel.narrative-engine.lint-fix-temperature:0.35}")
    private double narrativeLintFixTemperature;

    @Value("${novel.narrative-engine.lint-fix-max-tokens:4096}")
    private int narrativeLintFixMaxTokens;

    /** M4：跨章叙事承接（书本级持久化摘要注入初稿）；关闭则不拼接承接块。 */
    @Value("${novel.narrative-engine.m4-carryover-enabled:true}")
    private boolean narrativeM4CarryoverEnabled;

    /** M8：叙事批评 pass（默认关闭，多一次模型调用）。 */
    @Value("${novel.narrative-engine.m8-critic-enabled:false}")
    private boolean narrativeM8CriticEnabled;

    /** M8：在批评命中且达到最低严重度时做一轮窄幅重写（默认关闭）。 */
    @Value("${novel.narrative-engine.m8-rewrite-enabled:false}")
    private boolean narrativeM8RewriteEnabled;

    @Value("${novel.narrative-engine.m8-critic-temperature:0.25}")
    private double narrativeM8CriticTemperature;

    @Value("${novel.narrative-engine.m8-critic-max-tokens:1500}")
    private int narrativeM8CriticMaxTokens;

    @Value("${novel.narrative-engine.m8-rewrite-temperature:0.35}")
    private double narrativeM8RewriteTemperature;

    @Value("${novel.narrative-engine.m8-rewrite-max-tokens:4096}")
    private int narrativeM8RewriteMaxTokens;

    /** 触发窄幅重写所需的最低严重度：low / medium / high（大小写不敏感，可用 低/中/高）。 */
    @Value("${novel.narrative-engine.m8-rewrite-min-severity:medium}")
    private String narrativeM8RewriteMinSeverity;

    /** 叙事阻力层：Planner 阻力字段 + 初稿默认摩擦（防全程最优解）。 */
    @Value("${novel.narrative-engine.resistance-layer-enabled:true}")
    private boolean narrativeResistanceLayerEnabled;

    /** 流体润滑：终稿去 AI 味之后、Lint 之前多一次窄幅体验层修订。 */
    @Value("${novel.narrative-engine.flow-pass-enabled:true}")
    private boolean narrativeFlowPassEnabled;

    @Value("${novel.narrative-engine.flow-pass-temperature:0.35}")
    private double narrativeFlowPassTemperature;

    @Value("${novel.narrative-engine.flow-pass-max-tokens:4096}")
    private int narrativeFlowPassMaxTokens;

    /** 大纲：开篇须逐章细纲覆盖的最少章数。 */
    @Value("${novel.outline.detailed-prefix-chapters:40}")
    private int outlineDetailedPrefixChapters;

    /** 大纲：全书路线图须覆盖到的最少末章号。 */
    @Value("${novel.outline.min-roadmap-chapters:120}")
    private int outlineMinRoadmapChapters;

    /** 两阶段大纲：先产出冲突图谱 JSON，再生成 Markdown（效果最好；失败自动退回单阶段）。 */
    @Value("${novel.outline.two-phase-graph-enabled:true}")
    private boolean outlineTwoPhaseGraphEnabled;

    @Value("${novel.outline.graph-phase-temperature:0.35}")
    private double outlineGraphPhaseTemperature;

    @Value("${novel.outline.graph-phase-max-tokens:3600}")
    private int outlineGraphPhaseMaxTokens;

    /** 大纲图谱阶段：API 瞬时失败（如 Connection reset）时的最大尝试次数（含首次）。 */
    @Value("${novel.outline.graph-phase-max-attempts:3}")
    private int outlineGraphPhaseMaxAttempts;

    /** 图谱阶段重试间隔（毫秒）；0 表示立即连打。 */
    @Value("${novel.outline.graph-phase-retry-delay-ms:1000}")
    private long outlineGraphPhaseRetryDelayMs;

    /** 角色生成：从大纲 Markdown 摘取角色相关段落的上限字符。 */
    @Value("${novel.outline.character-excerpt-max-chars:14000}")
    private int outlineCharacterExcerptMaxChars;

    /** {@link #callAi}：大纲正文、初稿、角色、轻小说规划、终稿、sidecar、一致性修复等。 */
    @Value("${novel.llm.chat-max-attempts:3}")
    private int llmChatMaxAttempts;

    @Value("${novel.llm.chat-retry-delay-ms:1000}")
    private long llmChatRetryDelayMs;

    /**
     * 初稿 strip 后低于该字数则触发补救调用（避免模型只输出一两句）。
     * 与提示词中「总字数 2000-3000」配套；默认 1600 略低于目标下限以便兼容略有短缺。
     */
    @Value("${novel.llm.chapter-draft-min-chars:1600}")
    private int chapterDraftMinChars;

    /** 初稿过短时额外 full-prompt 补救次数（不含首次生成）。 */
    @Value("${novel.llm.chapter-draft-short-retries:2}")
    private int chapterDraftShortRetries;

    /** 章节成功后「角色动态状态」增量调用的温度（越低越保守）。 */
    @Value("${novel.generation.character-state-delta-temperature:0.25}")
    private double characterStateDeltaTemperature;

    /** 章节成功后「角色动态状态」增量 JSON 的 maxTokens。 */
    @Value("${novel.generation.character-state-delta-max-tokens:1200}")
    private int characterStateDeltaMaxTokens;

    /** {@link #callAiPlanner}：M2 章前规划。 */
    @Value("${novel.llm.planner-max-attempts:3}")
    private int llmPlannerMaxAttempts;

    @Value("${novel.llm.planner-retry-delay-ms:1000}")
    private long llmPlannerRetryDelayMs;

    /**
     * 文笔润色步骤里「大纲 + 书本设定 + 文风块」总字符上限；超出则保留前缀并标注截断，减轻超长 prompt 带来的排队与单次调用过久。
     */
    @Value("${novel.llm.polish-context-max-chars:18000}")
    private int polishContextMaxChars;

    /** false 时跳过步骤4文笔润色（直连模型仍慢或多任务抢占时可显著缩短单章耗时）。 */
    @Value("${novel.generation.polish-enabled:true}")
    private boolean generationPolishEnabled;

    public NovelGenerationAgent(
            ChatClient.Builder chatClientBuilder,
            ContentReviewAgent reviewAgent,
            PolishingAgent polishingAgent,
            ConsistencyReviewAgent consistencyAgent,
            ObjectMapper objectMapper,
            NarrativeProfileResolver narrativeProfileResolver,
            CognitionArcResolver cognitionArcResolver,
            ProseCraftResolver proseCraftResolver) {
        this.chatClient = chatClientBuilder.build();
        this.reviewAgent = reviewAgent;
        this.polishingAgent = polishingAgent;
        this.consistencyAgent = consistencyAgent;
        this.objectMapper = objectMapper;
        this.narrativeProfileResolver = narrativeProfileResolver;
        this.cognitionArcResolver = cognitionArcResolver;
        this.proseCraftResolver = proseCraftResolver;
        log.info("【AI代理初始化】NovelGenerationAgent 已就绪（生态型AI - 因果驱动版）");
    }

    public String generateOutline(String topic, String generationSetting, WritingPipeline pipeline) {
        return generateOutline(topic, generationSetting, pipeline, false, null);
    }

    public String generateOutline(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled) {
        return generateOutline(topic, generationSetting, pipeline, hotMemeEnabled, null);
    }

    public String generateOutline(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled,
                                  WritingStyleHints styleHints) {
        return generateOutline(topic, generationSetting, pipeline, hotMemeEnabled, styleHints, null, null, null);
    }

    /**
     * @param outlineDetailedOverride 非 null 时覆盖配置 {@code novel.outline.detailed-prefix-chapters}
     * @param outlineMinRoadmapOverride 非 null 时覆盖配置 {@code novel.outline.min-roadmap-chapters}
     * @param regenerationHint 非空时注入「作者/编辑补充说明」区块（重新生成大纲时使用；勿含未经转义的 {@code %} 序列以免干扰模板）
     */
    public String generateOutline(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled,
                                  WritingStyleHints styleHints, Integer outlineDetailedOverride, Integer outlineMinRoadmapOverride,
                                  String regenerationHint) {
        return generateOutlineResult(topic, generationSetting, pipeline, hotMemeEnabled, styleHints,
                outlineDetailedOverride, outlineMinRoadmapOverride, regenerationHint).markdown();
    }

    /**
     * 生成大纲：可选两阶段（冲突图谱 JSON → Markdown）；图谱见 {@link OutlineGenerationResult#outlineGraphJson()}。
     */
    public OutlineGenerationResult generateOutlineResult(String topic, String generationSetting, WritingPipeline pipeline,
                                                         boolean hotMemeEnabled, WritingStyleHints styleHints,
                                                         Integer outlineDetailedOverride, Integer outlineMinRoadmapOverride,
                                                         String regenerationHint) {
        log.info("【🤖 AI调用】开始生成故事大纲 - 题材: {}, 设定长度: {}, hotMeme={}, regenerateHint={}, twoPhaseGraph={}",
                topic, textLength(generationSetting), hotMemeEnabled,
                regenerationHint != null && !regenerationHint.isBlank(), outlineTwoPhaseGraphEnabled);
        long startTime = System.currentTimeMillis();
        int dp = outlineDetailedOverride != null ? outlineDetailedOverride : outlineDetailedPrefixChapters;
        int mr = outlineMinRoadmapOverride != null ? outlineMinRoadmapOverride : outlineMinRoadmapChapters;
        int[] clamped = clampOutlinePlanParams(dp, mr);
        dp = clamped[0];
        mr = clamped[1];

        String graphPretty = null;
        if (outlineTwoPhaseGraphEnabled) {
            graphPretty = tryGenerateConflictGraphJson(topic, generationSetting, pipeline, hotMemeEnabled, styleHints);
            if (graphPretty != null) {
                log.info("【大纲图谱】第一阶段成功，进入 Markdown 大纲第二阶段");
            }
        }

        String authorHintBlock = "";
        if (regenerationHint != null && !regenerationHint.isBlank()) {
            authorHintBlock = """

                    【作者/编辑补充说明（本次大纲须优先落实；若与全书硬性节奏结构冲突，在不颠覆长篇分期前提下尽量靠拢）】
                    """ + regenerationHint.trim();
        }

        String settingBlock = buildSettingBlock(generationSetting);
        String outlineCraft = NarrativeCraftPrompts.outlineAntiTropeBlock()
                + "\n" + NarrativeCraftPrompts.outlineReaderAppealBlock()
                + "\n" + NarrativeCraftPrompts.outlineChapterStructureDisciplineBlock()
                + "\n" + NarrativeCraftPrompts.outlineNarrativeKernelBlock(pipeline);
        if (isShuangwenPipeline(pipeline)) {
            outlineCraft = outlineCraft + "\n" + NarrativeCraftPrompts.outlineShuangwenUniversalBlock();
        } else {
            String outlinePipe = NarrativeCraftPrompts.outlinePipelineCraftBlock(pipeline);
            if (!outlinePipe.isBlank()) {
                outlineCraft = outlineCraft + "\n" + outlinePipe;
            }
        }
        if (hotMemeEnabled) {
            outlineCraft = outlineCraft + "\n" + NarrativeCraftPrompts.hotMemeOutlineBlock();
        }
        String micro = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
        if (!micro.isBlank()) {
            outlineCraft = outlineCraft + "\n" + micro;
        }

        String longFormBlock = NarrativeCraftPrompts.outlineLongFormRoadmapBlock(dp, mr);

        String head = String.format("""
                你是一位极具创意的全能型作家，擅长各种类型的小说创作。

                请根据题材"%s"创作一个新颖独特、符合题材特色的故事大纲。

                %s
                """, topic, settingBlock);

        String graphConsistencyLine = graphPretty != null
                ? "- **冲突结构概要**须与上文「已定稿 JSON」语义一致（可用叙述复述，不得引入矛盾的核心对立、张力矩阵与拐点逻辑）。\n"
                : "";
        String castConsistencyLine = graphPretty != null
                ? "6. **角色姓名**：必须与上文已定稿 JSON 中 cast 数组的 name 字段逐字一致；只可补充外貌与动机细节，禁止改名或另起一套核心姓名。\n"
                : "";

        String tail = String.format("""

                %s

                【核心要求】
                1. 理解题材特性，不要把所有题材都写成传统玄幻/修仙。
                2. 根据题材构建世界观、主角身份、核心矛盾、力量体系或特殊机制。
                3. 人物要立体真实，配角有独立目标，对立角色要有合理理念。
                4. 使用第三人称叙述，严禁第一人称。
                5. 必须优先遵守用户提供的设定，不能与设定冲突；设定不足处再自由发挥。
                %s

                %s

                【文风流水线】
                %s

                【输出格式】
                - 世界观设定
                - 主角设定
                - 主要角色（每人除表层外，尽量点明 **want / fear** 与一处 **内在矛盾**——欲求与行为的张力；对立角色写清「进场会搅乱什么」）
                - 对立角色
                - **冲突结构概要**（放在「剧情规划」之前；篇幅精炼，总宜 ≤ 400 字）
                  %s
                  - **引擎定性**一句话（须与上文「叙事内核」一致：如冲突升级型 / 关系漂移型 / 日常摩擦型 / 制度压抑型）
                  - **欲望与约束**：主角核心欲求一行；世界或规则的核心约束一行
                  - **三类张力**：外部压力、内心缺口或恐惧、关键关系张力 —— 各至少一条
                  - **人物张力矩阵**：至少三组「角色甲 ↔ 角色乙：关系定性」（称谓须与已定稿 JSON.cast[].name 一致）
                  - **全书张力走向**：4～8 个关键词或极短句（标注如何与长篇各阶段对齐）
                - 剧情规划（须严格遵守上文「长篇连载节奏」：(A)开篇细纲 +(B)后续路线图 +(C)全书宏观阶段与里程碑锚点；细纲每句遵守「章节推进纪律」括号标注）
                - 写作风格要求
                """, longFormBlock, castConsistencyLine, outlineCraft, styleGuide(pipeline), graphConsistencyLine);

        String graphAnchor = NarrativeCraftPrompts.outlineAnchoredGraphBlock(graphPretty != null ? graphPretty : "");
        String prompt = head + authorHintBlock + graphAnchor + tail;

        String markdown = callAi(prompt, startTime, "大纲生成");
        return new OutlineGenerationResult(markdown, graphPretty);
    }

    /** 第一阶段：冲突图谱 JSON；失败返回 null（上层退回单阶段大纲）。 */
    private String tryGenerateConflictGraphJson(String topic, String generationSetting, WritingPipeline pipeline,
                                               boolean hotMemeEnabled, WritingStyleHints styleHints) {
        String settingBlock = buildSettingBlock(generationSetting);
        String p1 = NarrativeCraftPrompts.outlineConflictGraphPhasePrompt(pipeline, topic, settingBlock, hotMemeEnabled);
        String micro = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
        if (!micro.isBlank()) {
            p1 = p1 + "\n\n" + micro;
        }
        String raw = callAiOutlineGraph(p1);
        JsonNode root = parseOutlineGraphJson(raw);
        if (root == null) {
            return null;
        }
        normalizeConflictGraph(root, pipeline);
        if (!validateConflictGraph(root)) {
            log.warn("【大纲图谱】JSON 结构校验未通过，退回单阶段大纲");
            return null;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("【大纲图谱】格式化失败: {}", e.getMessage());
            return null;
        }
    }

    private void normalizeConflictGraph(JsonNode root, WritingPipeline pipeline) {
        if (!(root instanceof ObjectNode on)) {
            return;
        }
        JsonNode altChars = on.path("characters");
        JsonNode castNode = on.path("cast");
        if ((!castNode.isArray() || castNode.size() == 0) && altChars.isArray() && altChars.size() > 0) {
            on.set("cast", altChars);
            on.remove("characters");
        }
        on.put("schemaVersion", root.path("schemaVersion").asInt(1));
        on.put("engine", NarrativeCraftPrompts.outlineEngineTokenForPipeline(pipeline));
    }

    private boolean validateConflictGraph(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        JsonNode setup = root.path("setup");
        if (!setup.path("desire").isTextual() || setup.path("desire").asText().isBlank()) {
            return false;
        }
        if (!setup.path("constraint").isTextual() || setup.path("constraint").asText().isBlank()) {
            return false;
        }
        JsonNode conflicts = root.path("conflicts");
        if (!conflicts.isArray() || conflicts.size() < 3) {
            return false;
        }
        boolean ext = false, internal = false, rel = false;
        for (JsonNode c : conflicts) {
            String type = c.path("type").asText("").toLowerCase(Locale.ROOT);
            if (type.contains("external")) {
                ext = true;
            }
            if (type.contains("internal")) {
                internal = true;
            }
            if (type.contains("relationship")) {
                rel = true;
            }
            if (!c.path("force").isTextual() || c.path("force").asText().isBlank()) {
                return false;
            }
        }
        if (!ext || !internal || !rel) {
            return false;
        }
        JsonNode tm = root.path("tension_matrix");
        if (!tm.isArray() || tm.size() < 3) {
            return false;
        }
        for (JsonNode e : tm) {
            if (!e.path("from").isTextual() || e.path("from").asText().isBlank()) {
                return false;
            }
            if (!e.path("to").isTextual() || e.path("to").asText().isBlank()) {
                return false;
            }
            if (!e.path("relation").isTextual() || e.path("relation").asText().isBlank()) {
                return false;
            }
        }
        JsonNode tp = root.path("turning_points");
        if (!tp.isArray() || tp.size() < 3) {
            return false;
        }
        for (JsonNode e : tp) {
            if (!e.path("label").isTextual() || e.path("label").asText().isBlank()) {
                return false;
            }
            if (!e.path("flip").isTextual() || e.path("flip").asText().isBlank()) {
                return false;
            }
        }
        JsonNode kw = root.path("escalation_keywords");
        if (!kw.isArray() || kw.size() < 6) {
            return false;
        }
        int nonEmptyKw = 0;
        for (JsonNode k : kw) {
            if (k.isTextual() && !k.asText().isBlank()) {
                nonEmptyKw++;
            }
        }
        if (nonEmptyKw < 6) {
            return false;
        }
        JsonNode branches = root.path("possible_branch_hints");
        if (!branches.isArray() || branches.size() < 2) {
            return false;
        }
        for (JsonNode b : branches) {
            if (!b.path("node").isTextual() || b.path("node").asText().isBlank()) {
                return false;
            }
            JsonNode out = b.path("outcomes");
            if (!out.isArray() || out.size() < 2) {
                return false;
            }
        }
        return validateOutlineCast(root);
    }

    /**
     * 两阶段大纲图谱中的结构化角色表 {@code cast}（可与 Markdown 大纲、角色档案生成对齐）。
     */
    private boolean validateOutlineCast(JsonNode root) {
        JsonNode cast = root.path("cast");
        if (!cast.isArray() || cast.size() < 4) {
            log.warn("【大纲图谱】cast 数组缺失或人数不足（须≥4）");
            return false;
        }
        boolean hasProtagonist = false;
        boolean hasAntagonist = false;
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (JsonNode c : cast) {
            if (c == null || !c.isObject()) {
                return false;
            }
            String name = c.path("name").asText("").trim();
            if (name.isEmpty() || name.length() > 80) {
                log.warn("【大纲图谱】cast 项 name 无效");
                return false;
            }
            if (!names.add(name)) {
                log.warn("【大纲图谱】cast 姓名重复: {}", name);
                return false;
            }
            if (c.path("want").asText("").trim().isEmpty()
                    || c.path("fear").asText("").trim().isEmpty()
                    || c.path("summary").asText("").trim().isEmpty()) {
                log.warn("【大纲图谱】cast 项缺少 want/fear/summary");
                return false;
            }
            if (!c.has("knowledge")) {
                log.warn("【大纲图谱】cast 项缺少 knowledge 键");
                return false;
            }
            String role = c.path("role").asText("").trim().toLowerCase(Locale.ROOT);
            if (roleContainsProtagonist(role)) {
                hasProtagonist = true;
            }
            if (roleContainsAntagonist(role)) {
                hasAntagonist = true;
            }
        }
        if (!hasProtagonist || !hasAntagonist) {
            log.warn("【大纲图谱】cast 须同时含主角位与对立位角色");
            return false;
        }
        return true;
    }

    private static boolean roleContainsProtagonist(String roleLower) {
        return roleLower.contains("protagonist") || roleLower.contains("主角");
    }

    private static boolean roleContainsAntagonist(String roleLower) {
        return roleLower.contains("antagonist")
                || roleLower.contains("反派")
                || roleLower.contains("对立")
                || roleLower.contains("对手");
    }

    private JsonNode parseOutlineGraphJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                int firstNl = cleaned.indexOf('\n');
                if (firstNl > 0) {
                    cleaned = cleaned.substring(firstNl + 1);
                }
                int fence = cleaned.lastIndexOf("```");
                if (fence >= 0) {
                    cleaned = cleaned.substring(0, fence);
                }
                cleaned = cleaned.trim();
            }
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("【大纲图谱】JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 大纲图谱专用调用：遇网络 I/O 异常或空响应时按配置重试（校验失败不计入此处，由上层退回单阶段）。
     */
    private String callAiOutlineGraph(String prompt) {
        log.debug("【大纲图谱】提示词长度: {} 字符", prompt.length());
        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(Math.max(0, Math.min(2, outlineGraphPhaseTemperature)))
                .maxTokens(Math.max(512, Math.min(8192, outlineGraphPhaseMaxTokens)))
                .build();
        int max = Math.max(1, Math.min(10, outlineGraphPhaseMaxAttempts));
        long delayMs = Math.max(0L, outlineGraphPhaseRetryDelayMs);
        Exception lastException = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                String result = chatClient.prompt(prompt).options(opts).call().content();
                long elapsed = System.currentTimeMillis() - t0;
                if (result != null && !result.isBlank()) {
                    log.info("【大纲图谱】✅ 第一阶段完成 - 第 {}/{} 次尝试 - 本请求耗时: {}ms, 长度: {}",
                            attempt, max, elapsed, result.length());
                    return result;
                }
                log.warn("【大纲图谱】第一阶段返回空文本 - 第 {}/{} 次尝试 - 本请求耗时: {}ms", attempt, max, elapsed);
            } catch (Exception e) {
                lastException = e;
                long elapsed = System.currentTimeMillis() - t0;
                log.warn("【大纲图谱】❌ 第一阶段失败 - 第 {}/{} 次尝试 - 本请求耗时: {}ms, {}",
                        attempt, max, elapsed, e.getMessage());
            }
            if (attempt < max && delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【大纲图谱】重试等待被中断，停止图谱阶段重试");
                    break;
                }
            }
        }
        if (lastException != null) {
            log.warn("【大纲图谱】第一阶段已用尽 {} 次尝试（最后一次异常: {}）", max, lastException.getMessage());
        }
        return "";
    }

    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, false,
                (WritingStyleHints) null, (NarrativeProfile) null, (NarrativePhysicsMode) null, (String) null,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled,
                (WritingStyleHints) null, (NarrativeProfile) null, (NarrativePhysicsMode) null, (String) null,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints,
                (NarrativeProfile) null, (NarrativePhysicsMode) null, (String) null,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    /**
     * @param narrativeProfile 可为 null：未开启叙事引擎或未解析时，将仅在开启引擎时用 resolver+管线回退默认（由调用方传入更佳）。
     */
    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints,
                                  NarrativeProfile narrativeProfile) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints, narrativeProfile,
                (NarrativePhysicsMode) null, (String) null,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    /**
     * @param narrativeCarryover M4：书本级承接摘要；可为 null。
     */
    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints,
                                  NarrativeProfile narrativeProfile, String narrativeCarryover) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints, narrativeProfile,
                (NarrativePhysicsMode) null, narrativeCarryover,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    /**
     * @param narrativePhysicsMode M6：可为 null 时按管线默认 {@link NarrativePhysicsMode#fromPipeline}。
     */
    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints,
                                  NarrativeProfile narrativeProfile, NarrativePhysicsMode narrativePhysicsMode,
                                  String narrativeCarryover) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints, narrativeProfile,
                narrativePhysicsMode, narrativeCarryover,
                (NarrativeEngineArtifactSink) null, (String) null, (String) null, (BooleanSupplier) null);
    }

    /**
     * @param artifactSink M7：可为 null；非 null 时写入 Planner/Lint 快照供落库。
     * @param writingStyleParamsJson 书本级文风 JSON；用于解析 {@code narrativeArcPhase}/{@code cognitionArc}，可为 null。
     */
    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints,
                                  NarrativeProfile narrativeProfile, NarrativePhysicsMode narrativePhysicsMode,
                                  String narrativeCarryover, NarrativeEngineArtifactSink artifactSink,
                                  String writingStyleParamsJson) {
        return generateChapter(outline, chapterNumber, previousContent, nextContent, characterProfile, previousChaptersSummary,
                novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints, narrativeProfile,
                narrativePhysicsMode, narrativeCarryover, artifactSink, writingStyleParamsJson,
                (String) null, (BooleanSupplier) null);
    }

    /**
     * @param characterNarrativeContextBlock 本章登场调度与动态状态摘录；可为 null。
     * @param chapterAbortRequested 非 null 且返回 true 时协作式中止（如任务已取消）；在两次 LLM 步骤之间检查，无法中断进行中的 HTTP 请求。
     */
    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline, boolean hotMemeEnabled, WritingStyleHints styleHints,
                                  NarrativeProfile narrativeProfile, NarrativePhysicsMode narrativePhysicsMode,
                                  String narrativeCarryover, NarrativeEngineArtifactSink artifactSink,
                                  String writingStyleParamsJson, String characterNarrativeContextBlock,
                                  BooleanSupplier chapterAbortRequested) {
        log.info("【📝 生态型AI】开始第{}章创作（因果驱动模式），hotMeme={}", chapterNumber, hotMemeEnabled);
        long totalStartTime = System.currentTimeMillis();
        NarrativeProfile effectiveProfile = resolveNarrativeProfile(pipeline, narrativeProfile);
        NarrativePhysicsMode effectivePhysics = narrativePhysicsMode != null
                ? narrativePhysicsMode
                : NarrativePhysicsMode.fromPipeline(pipeline);

        try {
            throwIfChapterAborted(chapterAbortRequested);
            log.info("【步骤1/5】调用创作Agent生成初稿...");
            String draft = generateDraft(outline, chapterNumber, previousContent, nextContent, characterProfile, novelSetting, chapterSetting, immutableConstraints, pipeline, hotMemeEnabled, styleHints, effectiveProfile, effectivePhysics, narrativeCarryover, artifactSink, writingStyleParamsJson, characterNarrativeContextBlock);
            log.info("【步骤1/5】✅ 初稿生成成功，长度: {} 字符", draft.length());

            throwIfChapterAborted(chapterAbortRequested);
            log.info("【步骤2/5】调用一致性审查Agent...");
            String consistencyProfile = mergeSettings(characterProfile, novelSetting, chapterSetting);
            String consistencyExtraShuang = isShuangwenPipeline(pipeline)
                    ? NarrativeCraftPrompts.consistencyReviewShuangwenUniversalBlock()
                    : "";
            String consistencyExtraPipe = isShuangwenPipeline(pipeline)
                    ? ""
                    : NarrativeCraftPrompts.consistencyReviewPipelineBlock(pipeline);
            String consistencyExtraMeme = hotMemeEnabled
                    ? NarrativeCraftPrompts.consistencyHotMemeReviewBlock()
                    : "";
            final String consistencyExtra = consistencyExtraShuang + consistencyExtraPipe + consistencyExtraMeme;
            String consistentContent = safeStep(
                    "一致性审查",
                    () -> consistencyAgent.reviewConsistency(draft, outline, consistencyProfile, chapterNumber, previousChaptersSummary, consistencyExtra),
                    draft
            );
            log.info("【步骤2/5】✅ 一致性审查完成，长度: {} 字符", consistentContent.length());

            throwIfChapterAborted(chapterAbortRequested);
            log.info("【步骤3/5】调用审核Agent进行内容审查...");
            String reviewedContent = safeStep("内容审查", () -> reviewAgent.reviewAndFix(consistentContent), consistentContent);
            log.info("【步骤3/5】✅ 内容审核完成，长度: {} 字符", reviewedContent.length());

            throwIfChapterAborted(chapterAbortRequested);
            log.info("【步骤4/5】调用润色Agent进行文笔优化...");
            String polishedContent;
            if (!generationPolishEnabled) {
                log.info("【步骤4/5】已跳过文笔润色（novel.generation.polish-enabled=false），沿用内容审核结果");
                polishedContent = reviewedContent;
            } else {
                String polishingOutline = mergeSettings(outline, novelSetting, chapterSetting);
                if (hotMemeEnabled) {
                    polishingOutline = polishingOutline + NarrativeCraftPrompts.hotMemePolishHintBlock();
                }
                String microPolish = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
                if (!microPolish.isBlank()) {
                    polishingOutline = polishingOutline + "\n\n" + microPolish;
                }
                String prosePolishBlock = NarrativeCraftPrompts.proseCraftPolishBlock(proseCraftResolver.resolve(writingStyleParamsJson));
                final String polishingOutlineFinal = polishingOutline + "\n\n【文风流水线】\n" + styleGuide(pipeline)
                        + (narrativeEngineEnabled && effectiveProfile != null
                        ? NarrativeCraftPrompts.narrativeEnginePolishReminder(effectiveProfile, pipeline, effectivePhysics)
                        : "")
                        + (prosePolishBlock.isBlank() ? "" : "\n\n" + prosePolishBlock);
                int polishBgLen = polishingOutlineFinal.strip().length();
                String polishContext = clipPolishContext(polishContextMaxChars, polishingOutlineFinal);
                if (polishContextMaxChars > 0 && polishBgLen > polishContextMaxChars) {
                    log.warn("【步骤4/5】润色背景上下文过长已截断：strip 后 {} -> 上限 {} 字符", polishBgLen, polishContextMaxChars);
                }
                log.info("【步骤4/5】即将请求润色模型（背景 {} 字符，待润色正文 {} 字符）", polishContext.length(), reviewedContent.length());
                polishedContent = safeStep(
                        "文笔润色",
                        () -> polishingAgent.polish(reviewedContent, polishContext, chapterNumber, pipeline),
                        reviewedContent
                );
            }
            log.info("【步骤4/5】✅ 润色优化完成，长度: {} 字符", polishedContent.length());

            throwIfChapterAborted(chapterAbortRequested);
            log.info("【步骤5/5】进行最终内容审核...");
            String reviewedFinalContent = safeStep("最终审核", () -> reviewAgent.reviewAndFix(polishedContent), polishedContent);
            String finalContent = safeStep("终稿去AI味", () -> deAiFinalize(reviewedFinalContent, chapterNumber, pipeline, hotMemeEnabled, styleHints, effectiveProfile, effectivePhysics), reviewedFinalContent);
            log.info("【步骤5/5】✅ 最终审核完成，长度: {} 字符", finalContent.length());

            throwIfChapterAborted(chapterAbortRequested);
            String afterFlow = applyNarrativeFlowPass(finalContent, chapterNumber, pipeline);
            throwIfChapterAborted(chapterAbortRequested);
            String afterLint = applyNarrativeLintPass(afterFlow, chapterNumber, pipeline, effectiveProfile, artifactSink);
            throwIfChapterAborted(chapterAbortRequested);
            String delivered = applyNarrativeM8Pass(outline, afterLint, chapterNumber, pipeline, effectiveProfile, artifactSink);

            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.info("【🎉 创作完成】第{}章创作流程结束，总耗时: {}ms", chapterNumber, totalElapsed);
            return delivered;
        } catch (ChapterGenerationAbortedException e) {
            throw e;
        } catch (Exception e) {
            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.error("【❌ 创作失败】第{}章创作异常 - 耗时: {}ms", chapterNumber, totalElapsed, e);
            throw e;
        }
    }

    private static void throwIfChapterAborted(BooleanSupplier chapterAbortRequested) {
        if (chapterAbortRequested != null && chapterAbortRequested.getAsBoolean()) {
            throw new ChapterGenerationAbortedException();
        }
    }

    private NarrativeProfile resolveNarrativeProfile(WritingPipeline pipeline, NarrativeProfile narrativeProfile) {
        if (!narrativeEngineEnabled) {
            return null;
        }
        if (narrativeProfile != null) {
            return narrativeProfile;
        }
        return narrativeProfileResolver.resolve(pipeline, null);
    }

    private String applyNarrativeLintPass(String content, int chapterNumber, WritingPipeline pipeline, NarrativeProfile profile,
                                          NarrativeEngineArtifactSink artifactSink) {
        if (content == null) {
            return null;
        }
        if (!narrativeEngineEnabled || profile == null) {
            if (artifactSink != null) {
                artifactSink.recordLintSkipped("narrative_engine_or_profile_off");
            }
            return content;
        }
        NarrativeLintReport report = NarrativeLint.lint(content, profile);
        if (!narrativeLintEnabled) {
            if (artifactSink != null) {
                artifactSink.recordLintScan(report, false, false, false);
            }
            return content;
        }
        if (!report.hasIssues()) {
            log.debug("【叙事Lint】第{}章 {}", chapterNumber, report.summary());
            if (artifactSink != null) {
                artifactSink.recordLintScan(report, true, false, false);
            }
            return content;
        }
        log.warn("【叙事Lint】第{}章 hits={} detail={}", chapterNumber, report.issues().size(), report.summary());
        if (!narrativeLintFixEnabled) {
            if (artifactSink != null) {
                artifactSink.recordLintScan(report, true, false, false);
            }
            return content;
        }
        String before = content;
        String fixed = safeStep(
                "叙事Lint窄幅修正",
                () -> polishingAgent.polishNarrativeLintPass(content, chapterNumber, pipeline, profile, report,
                        narrativeLintFixTemperature, narrativeLintFixMaxTokens),
                content
        );
        if (artifactSink != null) {
            artifactSink.recordLintScan(report, true, true, fixed != null && !fixed.equals(before));
        }
        return fixed;
    }

    /** 去 AI 味之后、Lint 之前：窄幅流体润滑（不改剧情）。 */
    private String applyNarrativeFlowPass(String content, int chapterNumber, WritingPipeline pipeline) {
        if (content == null || !narrativeFlowPassEnabled) {
            return content;
        }
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return safeStep(
                "叙事流体润滑",
                () -> polishingAgent.polishNarrativeFlowPass(content, chapterNumber, p,
                        narrativeFlowPassTemperature, narrativeFlowPassMaxTokens),
                content
        );
    }

    private static int m8MinSeverityOrdinal(String raw) {
        String s = (raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        if (s.equals("high") || s.equals("h") || s.contains("高")) {
            return 2;
        }
        if (s.equals("medium") || s.equals("med") || s.equals("m") || s.contains("中")) {
            return 1;
        }
        return 0;
    }

    /**
     * M8：叙事批评 → 可选一轮窄幅重写；失败或解析失败时保留正文。
     */
    private String applyNarrativeM8Pass(String outline, String content, int chapterNumber, WritingPipeline pipeline,
                                       NarrativeProfile profile, NarrativeEngineArtifactSink artifactSink) {
        if (content == null) {
            return null;
        }
        if (!narrativeEngineEnabled || profile == null) {
            if (artifactSink != null) {
                artifactSink.recordCriticSkipped("narrative_engine_or_profile_off");
            }
            return content;
        }
        if (!narrativeM8CriticEnabled) {
            if (artifactSink != null) {
                artifactSink.recordCriticSkipped("m8_critic_disabled");
            }
            return content;
        }

        String raw = safeStep("叙事批评", () -> polishingAgent.callNarrativeCritic(
                content,
                outline,
                chapterNumber,
                pipeline,
                profile,
                narrativeM8CriticTemperature,
                narrativeM8CriticMaxTokens), "");
        if (raw.isBlank()) {
            if (artifactSink != null) {
                artifactSink.recordCriticError("empty_or_failed_response", null);
            }
            return content;
        }

        NarrativeCriticReport report = NarrativeCriticReport.tryParse(objectMapper, raw);
        if (report == null) {
            if (artifactSink != null) {
                artifactSink.recordCriticError("parse_failed", raw);
            }
            return content;
        }

        if (!report.hasIssues()) {
            if (artifactSink != null) {
                artifactSink.recordCriticClean(raw);
            }
            return content;
        }

        if (artifactSink != null) {
            artifactSink.recordCriticHits(report, raw);
        }

        int minOrd = m8MinSeverityOrdinal(narrativeM8RewriteMinSeverity);
        boolean wantRewrite = narrativeM8RewriteEnabled && report.hasIssueAtLeast(minOrd);
        if (!wantRewrite) {
            if (artifactSink != null) {
                artifactSink.recordCriticRewrite(false, false);
            }
            return content;
        }

        String before = content;
        String fixed = safeStep(
                "叙事批评窄幅重写",
                () -> polishingAgent.polishNarrativeCriticPass(before, chapterNumber, pipeline, profile, report,
                        narrativeM8RewriteTemperature, narrativeM8RewriteMaxTokens),
                before
        );
        if (artifactSink != null) {
            artifactSink.recordCriticRewrite(true, fixed != null && !fixed.equals(before));
        }
        return fixed;
    }

    private String generateDraft(String outline, int chapterNumber, String previousContent, String nextContent,
                                 String characterProfile, String novelSetting, String chapterSetting,
                                 String immutableConstraints, WritingPipeline pipeline, boolean hotMemeEnabled,
                                 WritingStyleHints styleHints, NarrativeProfile narrativeProfile,
                                 NarrativePhysicsMode narrativePhysicsMode, String narrativeCarryover,
                                 NarrativeEngineArtifactSink artifactSink,
                                 String writingStyleParamsJson, String characterNarrativeContextBlock) {
        log.info("【🤖 创作Agent】开始生成第{}章初稿", chapterNumber);
        long startTime = System.currentTimeMillis();
        CognitionArcSnapshot cognitionArc = cognitionArcResolver.resolve(writingStyleParamsJson);
        ProseCraftSnapshot proseCraft = proseCraftResolver.resolve(writingStyleParamsJson);
        String draftRules = NarrativeCraftPrompts.chapterDraftHardRules(pipeline)
                + "\n" + NarrativeCraftPrompts.chapterReaderEngagementBlock(pipeline)
                + "\n" + NarrativeCraftPrompts.chapterTacticalCommandAntiTemplateBlock();
        if (isShuangwenPipeline(pipeline)) {
            draftRules = draftRules + "\n" + NarrativeCraftPrompts.chapterDraftShuangwenUniversalBlock();
        } else {
            String draftPipe = NarrativeCraftPrompts.chapterDraftPipelineCraftBlock(pipeline);
            if (!draftPipe.isBlank()) {
                draftRules = draftRules + "\n" + draftPipe;
            }
        }
        if (hotMemeEnabled) {
            draftRules = draftRules + "\n" + NarrativeCraftPrompts.hotMemeChapterDraftBlock();
        }
        String microDraft = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
        if (!microDraft.isBlank()) {
            draftRules = draftRules + "\n" + microDraft;
        }
        if (narrativeM4CarryoverEnabled && narrativeCarryover != null && !narrativeCarryover.isBlank()) {
            String carryBlock = NarrativeCraftPrompts.narrativeCarryoverBlock(narrativeCarryover.trim());
            if (!carryBlock.isBlank()) {
                draftRules = draftRules + "\n" + carryBlock;
            }
        }

        WritingPipeline draftPipeline = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        if (lightNovelChapterPlanningEnabled && draftPipeline == WritingPipeline.LIGHT_NOVEL) {
            String planBlock = buildLightNovelChapterPlanBlock(
                    chapterNumber, outline, previousContent, characterProfile,
                    novelSetting, chapterSetting, immutableConstraints);
            if (planBlock != null && !planBlock.isBlank()) {
                draftRules = draftRules + "\n\n" + planBlock;
            }
        }

        if (narrativeEngineEnabled && narrativeProfile != null) {
            String ne = NarrativeCraftPrompts.narrativeEngineChapterBlock(narrativeProfile);
            if (ne != null && !ne.isBlank()) {
                draftRules = draftRules + "\n\n" + ne;
            }
            String m5 = NarrativeCraftPrompts.narrativeEngineM5ChapterHardBlock(draftPipeline, narrativeProfile);
            if (m5 != null && !m5.isBlank()) {
                draftRules = draftRules + "\n\n" + m5;
            }
            NarrativePhysicsMode physics = narrativePhysicsMode != null
                    ? narrativePhysicsMode
                    : NarrativePhysicsMode.fromPipeline(draftPipeline);
            String m6 = NarrativeCraftPrompts.narrativeEngineM6PhysicsBlock(physics);
            if (m6 != null && !m6.isBlank()) {
                draftRules = draftRules + "\n\n" + m6;
            }
        }

        if (cognitionArc.enabled()) {
            String cogBlock = NarrativeCraftPrompts.cognitionArcChapterBlock(cognitionArc);
            if (cogBlock != null && !cogBlock.isBlank()) {
                draftRules = draftRules + "\n\n" + cogBlock;
            }
        }
        if (proseCraft.enabled()) {
            String proseBlock = NarrativeCraftPrompts.proseCraftChapterBlock(proseCraft);
            if (proseBlock != null && !proseBlock.isBlank()) {
                draftRules = draftRules + "\n\n" + proseBlock;
            }
        }

        boolean skipNarrativePlanner = !narrativeTwoPhaseEnabled || !narrativeEngineEnabled || narrativeProfile == null
                || (narrativeTwoPhaseSkipWithLightNovelPlan
                && draftPipeline == WritingPipeline.LIGHT_NOVEL
                && lightNovelChapterPlanningEnabled);
        if (skipNarrativePlanner) {
            if (artifactSink != null) {
                if (!narrativeTwoPhaseEnabled) {
                    artifactSink.recordPlannerSkipped("two_phase_disabled");
                } else if (!narrativeEngineEnabled) {
                    artifactSink.recordPlannerSkipped("narrative_engine_disabled");
                } else if (narrativeProfile == null) {
                    artifactSink.recordPlannerSkipped("no_profile");
                } else {
                    artifactSink.recordPlannerSkipped("light_novel_planner_skipped");
                }
            }
        } else {
            String nePlan = buildNarrativeTwoPhasePlannerBlock(
                    chapterNumber,
                    draftPipeline,
                    outline,
                    previousContent,
                    characterProfile,
                    novelSetting,
                    chapterSetting,
                    immutableConstraints,
                    narrativeProfile,
                    artifactSink,
                    cognitionArc);
            if (nePlan != null && !nePlan.isBlank()) {
                draftRules = draftRules + "\n\n" + nePlan;
            }
        }

        if (narrativeEngineEnabled && narrativeProfile != null && narrativeResistanceLayerEnabled
                && (skipNarrativePlanner || draftRules.contains("【本章叙事脚本｜Planner（M2）｜须严格遵守】") == false)) {
            draftRules = draftRules + "\n\n" + NarrativeCraftPrompts.narrativeResistanceSoftFallback(draftPipeline);
        }

        String previousSectionHeading = (draftPipeline == WritingPipeline.LIGHT_NOVEL && chapterNumber > 1)
                ? "【上一章衔接材料（结构化摘要+尾声节选｜禁止整章复述扩写）】"
                : "【上一章内容回顾】";

        String prompt = String.format("""
            你是一位精通网文节奏的顶级作家，采用"生态型AI"创作模式。

            【重要要求】
            - 必须使用第三人称叙述，严禁使用第一人称。
            - 不要出现"本章完""未完待续"等字样。
            - 所有事件必须有明确的因果来源，不得凭空捏造巧合事件。
            - 信息揭露必须符合延迟释放机制（每章最多揭露一个层级）。
            - 人物行为必须符合其目标/恐惧/知识状态。

            %s

            【故事大纲】
            %s

            %s

            【角色设定档案】
            %s

            %s

            %s

            %s

            【文风流水线】
            %s

            %s
            %s

            【下一章已存在内容（若为中间重生必须兼容）】
            %s

            【本章创作结构】
            1. 钩子：开篇给出危机、异常、压迫、误解或悬念。
            2. 压制：让主角面对困难或不公，但**用场面写体感**（对话顶撞、生理紧张、具体损失），避免只有交代设定没有心跳。
            3. 反转触发：用**情节层面的变化**制造跌宕（局势改写、信息翻盘、关键选择及后果、关系破裂或结盟等），至少一处要落在具体事件上；勿用密集「然而/其实/并非而是」类旁白句式替真正的剧情反转。
            4. 高潮爆发：冲突集中释放时，**情绪跟人物走**（台词、动作、后果），不单是旁白形容「很强很恐怖」。
            5. 余震：留下后续悬念，不要总结人生意义。

            【写作风格】
            - 总字数 2000-3000 字；禁止只输出片段、单段意象或一两句就收束。正文去首尾空白后若少于 %d 字视为不合格，须写满后再输出。
            - 长短句交错，允许情绪断裂；关键段落可有明显的快慢对比，避免通篇一个调门。
            - 加入1-2个真实生活细节。
            - 对话自然，人物行为符合设定但允许合理的矛盾和复杂性。
            - 严格保持核心角色名称稳定，不新增与核心角色高度相似的新名字。
            - 必须与上一章形成因果承接；若提供了下一章内容，本章结尾要自然过渡到下一章已发生事件。
            - 中间章节重生时，严禁推翻下一章已确定的关键事实（人物生死、地点、关键道具归属、阵营关系）。

            现在开始创作第%d章：
            """, draftRules,
                outline,
                narrativeSchedulingSection(characterNarrativeContextBlock),
                characterProfile,
                buildImmutableConstraintsBlock(immutableConstraints),
                buildSettingBlock(novelSetting),
                buildChapterSettingBlock(chapterSetting),
                styleGuide(pipeline),
                previousSectionHeading,
                previousContent,
                buildNextChapterBlock(nextContent),
                clampChapterDraftMinChars(chapterDraftMinChars),
                chapterNumber);

        String draft = callAi(prompt, startTime, "第" + chapterNumber + "章初稿生成（生态型）");
        return ensureChapterDraftMeetsMinLength(chapterNumber, prompt, draft, startTime);
    }

    private static int clampChapterDraftMinChars(int raw) {
        return Math.max(200, Math.min(12000, raw));
    }

    private static int clampChapterDraftShortRetries(int raw) {
        return Math.max(0, Math.min(5, raw));
    }

    /**
     * 初稿过短时附带同一完整上下文追加补救说明并重试，避免流水线后续在极短正文上浪费调用。
     */
    private String ensureChapterDraftMeetsMinLength(int chapterNumber, String basePrompt, String draft, long startTime) {
        int min = clampChapterDraftMinChars(chapterDraftMinChars);
        int extra = clampChapterDraftShortRetries(chapterDraftShortRetries);
        String current = draft == null ? "" : draft;
        for (int r = 0; r < extra; r++) {
            int len = current.strip().length();
            if (len >= min) {
                if (r > 0) {
                    log.info("【初稿补救】第{}章已达下限 strip={} >= {}", chapterNumber, len, min);
                }
                return current;
            }
            log.warn("【初稿过短】第{}章 strip 后 {} 字符 < 下限 {}，触发第 {}/{} 次补救调用",
                    chapterNumber, len, min, r + 1, extra);
            String fixPrompt = basePrompt + """

                    【硬性补救｜上次输出不合格】
                    你在上文指令之后给出的正文过短（去首尾空白仅约 %d 个有效字符），视同未完成本章。
                    你必须重新输出**完整第%d章正文**（仅小说正文，不要重复系统提示、不要markdown围栏、不要评语）。
                    - strip 后不少于 %d 字（目标仍建议 2000～3000 字）。
                    - 须有完整起承转合：钩子→事态推进→至少一处剧情层面的波折或信息变化→章末悬念。
                    - 若上一稿有可保留的情节或意象，请扩展并入全章，禁止只粘贴一两句。
                    """.formatted(len, chapterNumber, min);
            current = callAi(fixPrompt, startTime, "第" + chapterNumber + "章初稿生成（过短补救）");
        }
        int fin = current.strip().length();
        if (fin < min) {
            log.error("【初稿仍过短】第{}章 strip 后 {} 字符仍低于 {}（补救次数已用尽），后续步骤仍将执行",
                    chapterNumber, fin, min);
        }
        return current;
    }

    public String generateCharacterProfile(String topic, String generationSetting, WritingPipeline pipeline) {
        return generateCharacterProfile(topic, generationSetting, pipeline, false, null);
    }

    public String generateCharacterProfile(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled) {
        return generateCharacterProfile(topic, generationSetting, pipeline, hotMemeEnabled, null);
    }

    public String generateCharacterProfile(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled,
                                           WritingStyleHints styleHints) {
        return generateCharacterProfile(topic, generationSetting, pipeline, hotMemeEnabled, styleHints, null, null);
    }

    /**
     * @param outlineMarkdown        已定稿大纲正文；非空时摘取角色相关段落并锚定姓名
     * @param outlineGraphJson       冲突图谱 JSON；非空时摘录 setup/conflicts/tension_matrix
     */
    public String generateCharacterProfile(String topic, String generationSetting, WritingPipeline pipeline, boolean hotMemeEnabled,
                                           WritingStyleHints styleHints, String outlineMarkdown, String outlineGraphJson) {
        log.info("【🤖 AI调用】开始生成角色设定 - 题材: {}, 设定长度: {}, hotMeme={}, anchoredOutline={}, anchoredGraph={}",
                topic, textLength(generationSetting), hotMemeEnabled,
                outlineMarkdown != null && !outlineMarkdown.isBlank(),
                outlineGraphJson != null && !outlineGraphJson.isBlank());
        long startTime = System.currentTimeMillis();
        String characterExtra = NarrativeCraftPrompts.characterVoiceDifferentiationUniversalBlock();
        if (isShuangwenPipeline(pipeline)) {
            characterExtra = characterExtra + "\n" + NarrativeCraftPrompts.characterShuangwenUniversalBlock();
        } else {
            String pipeChar = NarrativeCraftPrompts.characterPipelineCraftBlock(pipeline);
            if (!pipeChar.isBlank()) {
                characterExtra = characterExtra + "\n" + pipeChar;
            }
        }
        if (hotMemeEnabled) {
            characterExtra = characterExtra.isEmpty()
                    ? NarrativeCraftPrompts.hotMemeCharacterProfileBlock()
                    : characterExtra + "\n" + NarrativeCraftPrompts.hotMemeCharacterProfileBlock();
        }
        String microChar = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
        if (!microChar.isBlank()) {
            characterExtra = characterExtra.isEmpty()
                    ? microChar
                    : characterExtra + "\n" + microChar;
        }

        String excerpt = OutlineCharacterExcerpt.extract(outlineMarkdown, outlineCharacterExcerptMaxChars);
        String graphAnchor = buildGraphCharacterAnchorJson(outlineGraphJson);
        String anchorBlock = NarrativeCraftPrompts.characterOutlineAnchorBlock(excerpt, graphAnchor);

        // 勿用 String.format：题材/设定中可能含 "%"。
        String prompt = """
                你是一位专业的角色设计师，擅长为各种题材创造立体丰满的角色。

                请为题材"
                """
                + (topic == null ? "" : topic)
                + """
                "创作符合题材特色、立体真实的角色档案。

                """
                + buildSettingBlock(generationSetting)
                + (anchorBlock.isBlank() ? "" : "\n" + anchorBlock)
                + """

                【要求】
                1. 必须优先遵守用户设定，角色身份、性格、关系、世界观不能与用户设定冲突。
                2. 主角要包含基本信息、外貌、性格、背景故事、特殊能力/身份/处境、行为模式、人际关系、内在矛盾。
                3. 设计2-3个重要配角，每个配角包含目标、作用、互动张力和成长弧线。
                4. 设计1-2个对立角色，包含理念、冲突点、个人魅力、原则和底线。
                5. 给出角色互动关系图和叙述视角说明。
                6. 使用第三人称叙述说明。

                """
                + characterExtra
                + """

                【文风流水线】
                """
                + styleGuide(pipeline)
                + """

                【重要】每个角色必须明确标注：
                - 目标（want）：角色最想要什么
                - 恐惧（fear）：角色最怕什么
                - 信息差（knowledge）：角色知道什么/不知道什么

                【输出格式要求】
                你必须只输出 JSON，且只能输出一个 JSON 对象，格式如下：
                {
                  "characters": [
                    {
                      "name": "角色名",
                      "type": "主角/配角/反派/下属单位/操作席位/子智能体（按人设）",
                      "want": "...",
                      "fear": "...",
                      "knowledge": "...",
                      "voice": "对白声纹（必填）：句长、称谓、口癖、报数习惯、情绪温度、坏消息说法等，可与其它角色明显区分",
                      "summary": "简短角色简介（职能与剧情作用，勿与 voice 重复空话）"
                    }
                  ]
                }
                严禁输出 markdown 代码块、解释文本、前后缀说明；除上述字段外不要增加其它键。
                """;

        return callAi(prompt, startTime, "角色设定生成");
    }

    /** 角色生成专用：从冲突图谱中抽出与姓名/关系对齐的字段，控制长度。 */
    private String buildGraphCharacterAnchorJson(String outlineGraphJson) {
        if (outlineGraphJson == null || outlineGraphJson.isBlank()) {
            return "";
        }
        try {
            JsonNode n = objectMapper.readTree(outlineGraphJson);
            ObjectNode out = objectMapper.createObjectNode();
            JsonNode cast = n.path("cast");
            if (cast.isArray() && !cast.isEmpty()) {
                out.set("cast", cast);
            }
            out.set("setup", n.path("setup"));
            out.set("conflicts", n.path("conflicts"));
            out.set("tension_matrix", n.path("tension_matrix"));
            String s = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
            int cap = 12000;
            return s.length() > cap ? s.substring(0, cap) + "\n...(truncated)" : s;
        } catch (Exception e) {
            log.warn("【角色锚定】冲突图谱摘录失败: {}", e.getMessage());
            return "";
        }
    }

    private String callAi(String prompt, long startTime, String actionName) {
        log.debug("【🤖 AI调用】{}提示词长度: {} 字符", actionName, prompt.length());
        int max = Math.max(1, Math.min(10, llmChatMaxAttempts));
        long delayMs = Math.max(0L, llmChatRetryDelayMs);
        Exception lastException = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                String result = chatClient.prompt(prompt).call().content();
                long reqElapsed = System.currentTimeMillis() - t0;
                if (result != null && !result.isBlank()) {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    log.info("【🤖 AI调用】✅ {}成功 - 第 {}/{} 次 - 本请求: {}ms, 累计: {}ms, 长度: {} 字符",
                            actionName, attempt, max, reqElapsed, totalElapsed, result.length());
                    return result;
                }
                log.warn("【🤖 AI调用】{}返回空 - 第 {}/{} 次 - 本请求: {}ms", actionName, attempt, max, reqElapsed);
            } catch (Exception e) {
                lastException = e;
                long reqElapsed = System.currentTimeMillis() - t0;
                log.warn("【🤖 AI调用】❌ {}失败 - 第 {}/{} 次 - 本请求: {}ms, {}",
                        actionName, attempt, max, reqElapsed, e.getMessage());
            }
            if (attempt < max && delayMs > 0) {
                sleepForLlmRetry(delayMs, "【🤖 AI调用】" + actionName);
            }
        }
        long totalElapsed = System.currentTimeMillis() - startTime;
        log.error("【🤖 AI调用】❌ {}已用尽 {} 次尝试 - 累计: {}ms", actionName, max, totalElapsed, lastException);
        if (lastException != null) {
            if (lastException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(lastException);
        }
        throw new IllegalStateException(actionName + "：LLM 返回为空（已重试 " + max + " 次）");
    }

    /**
     * 章节落库后可选步骤：根据正文摘录产出角色动态状态增量 JSON（失败时由调用方忽略）。
     */
    public String generateCharacterStateDeltaJson(int chapterNumber, String chapterExcerpt, String castLines,
                                                  String existingStatesSummary) {
        long startTime = System.currentTimeMillis();
        String prompt = NarrativeCraftPrompts.characterStateDeltaPrompt(chapterNumber, chapterExcerpt, castLines, existingStatesSummary);
        return callAiCharacterStateDelta(prompt, startTime);
    }

    private String callAiCharacterStateDelta(String prompt, long startTime) {
        log.debug("【角色状态增量】提示词长度: {} 字符", prompt.length());
        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(Math.max(0, Math.min(1, characterStateDeltaTemperature)))
                .maxTokens(Math.max(256, Math.min(4096, characterStateDeltaMaxTokens)))
                .build();
        int max = Math.max(1, Math.min(10, llmChatMaxAttempts));
        long delayMs = Math.max(0L, llmChatRetryDelayMs);
        Exception lastException = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                String result = chatClient.prompt(prompt).options(opts).call().content();
                long reqElapsed = System.currentTimeMillis() - t0;
                if (result != null && !result.isBlank()) {
                    log.info("【角色状态增量】✅ 第 {}/{} 次 - {}ms, 长度 {}",
                            attempt, max, reqElapsed, result.length());
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("【角色状态增量】❌ 第 {}/{} 次: {}", attempt, max, e.getMessage());
            }
            if (attempt < max && delayMs > 0) {
                sleepForLlmRetry(delayMs, "【角色状态增量】");
            }
        }
        log.warn("【角色状态增量】已用尽 {} 次: {}", max, lastException != null ? lastException.getMessage() : "空响应");
        return "";
    }

    /** M2 Planner：低温、短输出；失败不抛给上层。 */
    private String callAiPlanner(String prompt, long startTime, String actionName) {
        log.debug("【🤖 Planner】{}提示词长度: {} 字符", actionName, prompt.length());
        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(Math.max(0, Math.min(2, narrativePlannerTemperature)))
                .maxTokens(Math.max(256, Math.min(4096, narrativePlannerMaxTokens)))
                .build();
        int max = Math.max(1, Math.min(10, llmPlannerMaxAttempts));
        long delayMs = Math.max(0L, llmPlannerRetryDelayMs);
        Exception lastException = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                String result = chatClient.prompt(prompt).options(opts).call().content();
                long reqElapsed = System.currentTimeMillis() - t0;
                if (result != null && !result.isBlank()) {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    log.info("【🤖 Planner】✅ {}成功 - 第 {}/{} 次 - 本请求: {}ms, 累计: {}ms, 长度: {} 字符",
                            actionName, attempt, max, reqElapsed, totalElapsed, result.length());
                    return result;
                }
                log.warn("【🤖 Planner】{}返回空 - 第 {}/{} 次 - 本请求: {}ms", actionName, attempt, max, reqElapsed);
            } catch (Exception e) {
                lastException = e;
                long reqElapsed = System.currentTimeMillis() - t0;
                log.warn("【🤖 Planner】❌ {}失败 - 第 {}/{} 次 - 本请求: {}ms, {}",
                        actionName, attempt, max, reqElapsed, e.getMessage());
            }
            if (attempt < max && delayMs > 0) {
                sleepForLlmRetry(delayMs, "【🤖 Planner】" + actionName);
            }
        }
        long totalElapsed = System.currentTimeMillis() - startTime;
        log.warn("【🤖 Planner】{}已用尽 {} 次尝试，静默跳过 - 累计: {}ms, {}",
                actionName, max, totalElapsed, lastException != null ? lastException.getMessage() : "空响应");
        return "";
    }

    private void sleepForLlmRetry(long delayMs, String logPrefix) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("{} 重试等待被中断", logPrefix);
        }
    }

    private String narrativeProfileForPlannerPrompt(NarrativeProfile p) {
        if (p == null) return "（无）";
        StringBuilder sb = new StringBuilder();
        sb.append("- 主情绪类型：").append(p.emotionType()).append("\n");
        sb.append(String.format(Locale.ROOT, "- 强度带宽：[%.2f, %.2f]；压抑度：%.2f\n",
                p.intensityMin(), p.intensityMax(), p.suppression()));
        if (p.triggerFact() != null && !p.triggerFact().isBlank()) {
            sb.append("- 触发事实锚点：").append(p.triggerFact()).append("\n");
        }
        if (p.rhythmHint() != null && !p.rhythmHint().isBlank()) {
            sb.append("- 节奏意图：").append(p.rhythmHint()).append("\n");
        }
        if (p.textureHint() != null && !p.textureHint().isBlank()) {
            sb.append("- 语言材质：").append(p.textureHint()).append("\n");
        }
        if (p.affection() != null || p.awkwardness() != null || p.assertiveness() != null) {
            sb.append("- 轻小说微情绪维：");
            if (p.affection() != null) sb.append(String.format(Locale.ROOT, "好感%.2f ", p.affection()));
            if (p.awkwardness() != null) sb.append(String.format(Locale.ROOT, "别扭%.2f ", p.awkwardness()));
            if (p.assertiveness() != null) sb.append(String.format(Locale.ROOT, "主动%.2f", p.assertiveness()));
            sb.append("\n");
        }
        if (!p.forbiddenLines().isEmpty()) {
            sb.append("- 禁止项：").append(String.join("；", p.forbiddenLines())).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildNarrativeTwoPhasePlannerBlock(int chapterNumber,
                                                      WritingPipeline pipeline,
                                                      String outline,
                                                      String previousContent,
                                                      String characterProfile,
                                                      String novelSetting,
                                                      String chapterSetting,
                                                      String immutableConstraints,
                                                      NarrativeProfile profile,
                                                      NarrativeEngineArtifactSink artifactSink,
                                                      CognitionArcSnapshot cognitionArc) {
        long startTime = System.currentTimeMillis();
        String outlineClip = clipForPlan(outline, NE_PLAN_OUTLINE_CLIP);
        String profileClip = clipForPlan(characterProfile, NE_PLAN_PROFILE_CLIP);
        String prevClip = clipForPlan(previousContent, NE_PLAN_PREV_CLIP);
        String immutableClip = clipForPlan(immutableConstraints, NE_PLAN_IMMUTABLE_CLIP);
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String plannerEngineParams = narrativeProfileForPlannerPrompt(profile);
        String cognitionPlanner = NarrativeCraftPrompts.cognitionArcPlannerHint(cognitionArc);
        if (!cognitionPlanner.isBlank()) {
            plannerEngineParams = plannerEngineParams + "\n\n" + cognitionPlanner;
        }
        String prompt = String.format("""
                你是网络小说叙事策划编辑。请只输出一个 JSON 对象，禁止 markdown 代码块、禁止解释前后缀。

                【全书大纲（节选）】
                %s

                【角色档案（节选）】
                %s

                %s

                %s

                %s

                【叙事引擎参数（须遵守其强度带宽与禁止项）】
                %s

                【上一章衔接材料（勿照搬扩写；规划连贯即可）】
                %s

                【文风流水线】%s

                【任务】为第 %d 章制定「叙事脚本」（服务于正文写作，不是写正文）：
                1. mainObjective：本章唯一主线目标，一句话。
                2. emotionArc：字符串数组，3～6 条，描述本章情绪走向（可含约强度如「压抑→抬头→回落」），须落在叙事引擎给出的强度带宽内波动，避免全程同一强度。
                3. beats：3～5 条可演出场面（谁、在哪、发生什么），顺序即正文推荐顺序。
                4. opening：开场钩子一句话。
                5. closing：结尾悬念或转折一句话；禁止「下一章预告」元叙事。
                6. mustAvoid：2～5 条短语，列出衔接材料里已交代、正文禁止再长篇复述的信息点。
                7. reasonShort：一两句说明为何如此分配节奏（可给正文作者看）。
                %s

                JSON 字段名必须完全一致（阻力三项可同时给出 camelCase 与 snake_case 之一即可）：
                {
                  "mainObjective": "...",
                  "emotionArc": ["...", "..."],
                  "beats": ["...", "..."],
                  "opening": "...",
                  "closing": "...",
                  "mustAvoid": ["...", "..."],
                  "reasonShort": "...",
                  "expectedObstacle": "...",
                  "riskPoint": "...",
                  "delayMechanism": "..."
                }
                """,
                outlineClip.isBlank() ? "（无）" : outlineClip,
                profileClip.isBlank() ? "（无）" : profileClip,
                buildSettingBlock(novelSetting),
                buildChapterSettingBlock(chapterSetting),
                buildImmutableConstraintsBlock(immutableClip.isBlank() ? null : immutableClip),
                plannerEngineParams,
                prevClip.isBlank() ? "（无：第1章或无前文）" : prevClip,
                p.name(),
                chapterNumber,
                plannerM2ResistanceTaskLines(p));
        try {
            String raw = callAiPlanner(prompt, startTime, "叙事引擎M2章前规划");
            String formatted = formatNarrativePlannerJson(raw, p, narrativeResistanceLayerEnabled && narrativeEngineEnabled);
            if (artifactSink != null) {
                if (formatted != null && !formatted.isBlank()) {
                    artifactSink.recordPlannerApplied(raw, formatted);
                } else {
                    artifactSink.recordPlannerError("planner_empty_or_parse_failed");
                }
            }
            if (formatted != null && !formatted.isBlank()) {
                log.info("【叙事引擎M2】第{}章 Planner 脚本已注入初稿提示词", chapterNumber);
            }
            return formatted == null ? "" : formatted;
        } catch (Exception e) {
            log.warn("【叙事引擎M2】第{}章 Planner 异常，跳过: {}", chapterNumber, e.getMessage());
            if (artifactSink != null) {
                artifactSink.recordPlannerError(e.getMessage() == null ? "planner_exception" : e.getMessage());
            }
            return "";
        }
    }

    /** M2 Planner：阻力三项任务说明；日常向允许「无冲突」章三项填「无」并由 reasonShort 兜底。 */
    private static String plannerM2ResistanceTaskLines(WritingPipeline p) {
        if (p == WritingPipeline.SLICE_OF_LIFE) {
            return """
                    8. expectedObstacle（或 expected_obstacle）：**日常向**——生活层「小别扭」（碍于面子没说清、时间不够、计划被打岔）可写一句；**纯氛围、关系沉淀、几乎无外部事件**的章可填「无」，并在 reasonShort 说明本章立意（禁止为凑数硬造狗血或危机）。
                    9. riskPoint（或 risk_point）：轻微节外生枝即可（岔开话题、小事耽搁）；无事冲突章可填「无」。
                    10. delayMechanism（或 delay_mechanism）：情绪或琐事导致的半步延误即可；静水章可填「无」。
                    """;
        }
        return """
                8. expectedObstacle（或 expected_obstacle）：本章须出现的一次「非最优决策、意外阻断、或须付出代价的选择」，一句话；若无请写「无」并在 reasonShort 里说明为何本章仍成立。
                9. riskPoint（或 risk_point）：一个可能失败或节外生枝的动作节点，一句话。
                10. delayMechanism（或 delay_mechanism）：成功或兑现为何不能一次到位、须二次尝试或补救，一句话。
                """;
    }

    private String formatNarrativePlannerJson(String raw, WritingPipeline pipeline, boolean resistanceLayerEnabled) {
        if (raw == null || raw.isBlank()) return "";
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        try {
            JsonNode root = objectMapper.readTree(extractFirstJsonObject(raw));
            String main = root.path("mainObjective").asText("").trim();
            if (main.isEmpty()) {
                main = root.path("main_objective").asText("").trim();
            }
            String opening = root.path("opening").asText("").trim();
            String closing = root.path("closing").asText("").trim();
            String reason = root.path("reasonShort").asText("").trim();
            if (reason.isEmpty()) {
                reason = root.path("reason_short").asText("").trim();
            }

            List<String> arc = new ArrayList<>();
            JsonNode arcNode = root.path("emotionArc");
            if (!arcNode.isArray()) {
                arcNode = root.path("emotion_arc");
            }
            if (arcNode.isArray()) {
                for (JsonNode a : arcNode) {
                    String line = a.asText("").trim();
                    if (!line.isEmpty()) arc.add(line);
                }
            }

            List<String> beats = new ArrayList<>();
            JsonNode beatsNode = root.path("beats");
            if (beatsNode.isArray()) {
                for (JsonNode b : beatsNode) {
                    String line = b.asText("").trim();
                    if (!line.isEmpty()) beats.add(line);
                }
            }

            List<String> avoids = new ArrayList<>();
            JsonNode avoidNode = root.path("mustAvoid");
            if (!avoidNode.isArray()) {
                avoidNode = root.path("must_avoid");
            }
            if (avoidNode.isArray()) {
                for (JsonNode a : avoidNode) {
                    String line = a.asText("").trim();
                    if (!line.isEmpty()) avoids.add(line);
                }
            }

            if (main.isEmpty() && beats.isEmpty()) {
                return "";
            }

            String obs = plannerResistanceTextOr(root, "expectedObstacle", "expected_obstacle");
            String risk = plannerResistanceTextOr(root, "riskPoint", "risk_point");
            String delay = plannerResistanceTextOr(root, "delayMechanism", "delay_mechanism");
            boolean hasResistanceFields = plannerResistanceMeaningful(obs) || plannerResistanceMeaningful(risk)
                    || plannerResistanceMeaningful(delay);

            StringBuilder sb = new StringBuilder();
            sb.append("【本章叙事脚本｜Planner（M2）｜须严格遵守】\n");
            if (!main.isEmpty()) {
                sb.append("- 本章唯一主线：").append(main).append("\n");
            }
            if (!arc.isEmpty()) {
                sb.append("- 情绪弧线（正文须体现起伏，勿平推）：\n");
                for (int i = 0; i < arc.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(arc.get(i)).append("\n");
                }
            }
            if (!opening.isEmpty()) {
                sb.append("- 开场钩子：").append(opening).append("\n");
            }
            if (!beats.isEmpty()) {
                sb.append("- 节拍（正文须按序落地，可用对白与场面扩展，不得另起平行主线取代）：\n");
                for (int i = 0; i < beats.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(beats.get(i)).append("\n");
                }
            }
            if (!closing.isEmpty()) {
                sb.append("- 结尾钩子：").append(closing).append("\n");
            }
            if (!avoids.isEmpty()) {
                sb.append("- 禁止复述（只可一句带过或不提）：").append(String.join("；", avoids)).append("\n");
            }
            if (!reason.isEmpty()) {
                sb.append("- 策划说明：").append(reason).append("\n");
            }
            if (resistanceLayerEnabled) {
                if (hasResistanceFields) {
                    boolean slice = p == WritingPipeline.SLICE_OF_LIFE;
                    sb.append(slice ? "\n【本章节奏与微摩擦｜Planner】\n" : "\n【本章阻力纪律｜Planner】\n");
                    if (plannerResistanceMeaningful(obs)) {
                        sb.append(slice ? "- 生活层别扭/心气：" : "- 预期障碍/代价选择：").append(obs).append("\n");
                    }
                    if (plannerResistanceMeaningful(risk)) {
                        sb.append(slice ? "- 轻微波折：" : "- 风险节点：").append(risk).append("\n");
                    }
                    if (plannerResistanceMeaningful(delay)) {
                        sb.append(slice ? "- 半步延误：" : "- 延误/二次机制：").append(delay).append("\n");
                    }
                    sb.append(slice
                            ? "- 【执行】日常向以生活可信为先：有则轻轻落地，无则勿硬拧升格；结局与人物关系仍须服从大纲。\n"
                            : "- 【执行】上述须在正文中有可见落地，不得口号式一笔带过；结局事实仍须服从大纲。\n");
                } else {
                    sb.append("\n").append(NarrativeCraftPrompts.narrativeResistanceSoftFallback(p)).append("\n");
                }
            }
            sb.append("- 【脚本服从】本节为章节叙事骨架；禁止用复述衔接材料填满篇幅取代节拍。\n");
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("【叙事引擎M2】JSON 解析失败: {}", e.getMessage());
            return "";
        }
    }

    private static String plannerResistanceTextOr(JsonNode root, String camel, String snake) {
        String a = root.path(camel).asText("").trim();
        if (!a.isEmpty()) {
            return a;
        }
        return root.path(snake).asText("").trim();
    }

    private static boolean plannerResistanceMeaningful(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return false;
        }
        return !t.equals("无") && !t.equals("暂无") && !t.equals("无。");
    }

    private String buildSettingBlock(String setting) {
        if (setting == null || setting.trim().isEmpty()) {
            return "【用户补充设定】\n无。";
        }
        return "【用户补充设定】\n" + setting.trim();
    }

    private String buildChapterSettingBlock(String setting) {
        if (setting == null || setting.trim().isEmpty()) {
            return "【本章续写设定】\n无。";
        }
        return "【本章续写设定】\n" + setting.trim();
    }

    /** 置于大纲与角色档案之间的本章调度块；关闭功能或无内容时不输出占位，避免干扰模型。 */
    private static String narrativeSchedulingSection(String characterNarrativeContextBlock) {
        if (characterNarrativeContextBlock == null || characterNarrativeContextBlock.isBlank()) {
            return "";
        }
        return "【本章叙事调度｜登场焦点与动态状态】\n" + characterNarrativeContextBlock.strip();
    }

    private String buildNextChapterBlock(String nextContent) {
        if (nextContent == null || nextContent.trim().isEmpty()) {
            return "无。";
        }
        String normalized = nextContent.trim();
        return normalized.length() <= 1500 ? normalized : normalized.substring(0, 1500) + "\n...(已截断)";
    }

    /** 轻小说：章前节拍规划（JSON），失败返回空串以便跳过。 */
    private String buildLightNovelChapterPlanBlock(int chapterNumber, String outline, String previousContent,
                                                  String characterProfile, String novelSetting, String chapterSetting,
                                                  String immutableConstraints) {
        long startTime = System.currentTimeMillis();
        String outlineClip = clipForPlan(outline, LN_PLAN_OUTLINE_CLIP);
        String profileClip = clipForPlan(characterProfile, LN_PLAN_PROFILE_CLIP);
        String prevClip = clipForPlan(previousContent, LN_PLAN_PREV_CLIP);
        String immutableClip = clipForPlan(immutableConstraints, LN_PLAN_IMMUTABLE_CLIP);
        String prompt = String.format("""
                你是轻小说章节策划编辑。请只输出一个 JSON 对象，禁止 markdown 代码块、禁止解释前后缀。

                【全书大纲（节选）】
                %s

                【角色档案（节选）】
                %s

                %s

                %s

                %s

                【上一章衔接或回顾材料（勿照搬扩写；规划时考虑连贯即可）】
                %s

                【任务】为第 %d 章制定写作节拍：
                1. 本章只能有一条主线目标（mainObjective），所有场面服务这条线。
                2. beats 数组必须 3～5 条，每条是可演出的具体场面（谁、在哪、发生什么），禁止空话设定清单。
                3. opening：开场钩子一句话（危机/误会/反常其一）。
                4. closing：结尾具体的悬念或场面转折一句话；禁止「下一章预告」类元叙事。
                5. mustAvoid：2～5 条短语，列出衔接材料里已交代、正文禁止再长篇复述的信息点。

                JSON 格式（字段名必须一致）：
                {
                  "mainObjective": "...",
                  "beats": ["...", "...", "..."],
                  "opening": "...",
                  "closing": "...",
                  "mustAvoid": ["...", "..."]
                }
                """,
                outlineClip.isBlank() ? "（无）" : outlineClip,
                profileClip.isBlank() ? "（无）" : profileClip,
                buildSettingBlock(novelSetting),
                buildChapterSettingBlock(chapterSetting),
                buildImmutableConstraintsBlock(immutableClip.isBlank() ? null : immutableClip),
                prevClip.isBlank() ? "（无：第1章或无前文）" : prevClip,
                chapterNumber);
        try {
            String raw = callAi(prompt, startTime, "轻小说章拍规划");
            String formatted = formatLightNovelPlanJson(raw);
            if (!formatted.isBlank()) {
                log.info("【轻小说章拍规划】第{}章节拍已注入初稿提示词", chapterNumber);
            }
            return formatted;
        } catch (Exception e) {
            log.warn("【轻小说章拍规划】第{}章失败，跳过节拍注入: {}", chapterNumber, e.getMessage());
            return "";
        }
    }

    private static String clipForPlan(String text, int maxChars) {
        if (text == null || text.isBlank()) return "";
        String t = text.trim();
        return t.length() <= maxChars ? t : t.substring(0, maxChars) + "\n...(已截断)";
    }

    private static String extractFirstJsonObject(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        int i = t.indexOf('{');
        int j = t.lastIndexOf('}');
        if (i >= 0 && j > i) {
            return t.substring(i, j + 1);
        }
        return t;
    }

    private String formatLightNovelPlanJson(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(extractFirstJsonObject(raw));
            String main = root.path("mainObjective").asText("").trim();
            String opening = root.path("opening").asText("").trim();
            String closing = root.path("closing").asText("").trim();
            JsonNode beatsNode = root.path("beats");
            JsonNode avoidNode = root.path("mustAvoid");

            List<String> beats = new ArrayList<>();
            if (beatsNode.isArray()) {
                for (JsonNode b : beatsNode) {
                    String line = b.asText("").trim();
                    if (!line.isEmpty()) {
                        beats.add(line);
                    }
                }
            }
            List<String> avoids = new ArrayList<>();
            if (avoidNode.isArray()) {
                for (JsonNode a : avoidNode) {
                    String line = a.asText("").trim();
                    if (!line.isEmpty()) {
                        avoids.add(line);
                    }
                }
            }
            if (main.isEmpty() && beats.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【本章节拍规划｜须严格遵守】\n");
            if (!main.isEmpty()) {
                sb.append("- 本章唯一主线：").append(main).append("\n");
            }
            if (!opening.isEmpty()) {
                sb.append("- 开场钩子：").append(opening).append("\n");
            }
            if (!beats.isEmpty()) {
                sb.append("- 节拍（正文须按序落地，可用对白与场面扩展，不得跳过）：\n");
                for (int i = 0; i < beats.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(beats.get(i)).append("\n");
                }
            }
            if (!closing.isEmpty()) {
                sb.append("- 结尾钩子：").append(closing).append("\n");
            }
            if (!avoids.isEmpty()) {
                sb.append("- 禁止复述（只可一句带过或不提）：");
                sb.append(String.join("；", avoids)).append("\n");
            }
            sb.append("- 【节拍服从】上文节拍即本章骨架：禁止另起一套平行剧情取代节拍；禁止用复述衔接材料填满篇幅。\n");
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("【轻小说章拍规划】JSON 解析失败: {}", e.getMessage());
            return "";
        }
    }

    private String mergeSettings(String base, String novelSetting, String chapterSetting) {
        StringBuilder builder = new StringBuilder();
        if (base != null && !base.trim().isEmpty()) {
            builder.append(base.trim()).append("\n\n");
        }
        builder.append(buildSettingBlock(novelSetting)).append("\n\n");
        builder.append(buildChapterSettingBlock(chapterSetting));
        return builder.toString();
    }

    public String reviseChapterForConsistency(String chapterContent, String immutableConstraints, String issueHint) {
        long startTime = System.currentTimeMillis();
        String prompt = String.format("""
            你是严谨的小说统稿编辑，请仅修复一致性问题，不改剧情走向，不缩水，不新增无关设定。

            【一致性硬约束】
            %s

            【发现的问题】
            %s

            【待修复章节】
            %s

            【修复要求】
            1. 优先修复人名一致性问题（尤其主角、女主、反派核心角色）。
            2. 保持第三人称和原文风格，不要引入说明性语句。
            3. 不要输出解释，只返回修复后的完整正文。
            """, buildImmutableConstraintsBlock(immutableConstraints), issueHint == null ? "无" : issueHint, chapterContent);
        return callAi(prompt, startTime, "章节一致性修复");
    }

    private String buildImmutableConstraintsBlock(String immutableConstraints) {
        if (immutableConstraints == null || immutableConstraints.trim().isEmpty()) {
            return "【一致性硬约束】\n无。";
        }
        return "【一致性硬约束】\n" + immutableConstraints.trim();
    }

    /** 前端可传入的规划参数 clamp，再交给 {@link NarrativeCraftPrompts#outlineLongFormRoadmapBlock} 做二次下限。 */
    private static int[] clampOutlinePlanParams(int detailedPrefix, int minRoadmap) {
        int dp = Math.min(150, Math.max(15, detailedPrefix));
        int mr = Math.min(600, Math.max(minRoadmap, dp + 15));
        return new int[] { dp, mr };
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    @FunctionalInterface
    private interface ChapterStep {
        String run() throws Exception;
    }

    /** 限制润色步骤背景块体积，避免单次请求 prompt 过大拖垮网关或排队过久。 */
    private static String clipPolishContext(int maxChars, String outlineAndMeta) {
        if (outlineAndMeta == null) {
            return "";
        }
        String t = outlineAndMeta.strip();
        if (maxChars <= 0 || t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars)
                + "\n\n【系统提示：上文大纲与设定因过长已截断；润色以本章正文为主，勿编造未出现的设定。】";
    }

    private String safeStep(String stepName, ChapterStep step, String fallback) {
        try {
            String result = step.run();
            if (result == null || result.trim().isEmpty()) {
                log.warn("【{}】返回空内容，使用回退内容继续流程", stepName);
                return fallback;
            }
            return result;
        } catch (Exception e) {
            log.warn("【{}】失败，使用回退内容继续流程: {}", stepName, e.getMessage());
            return fallback;
        }
    }

    private String deAiFinalize(String content, int chapterNumber, WritingPipeline pipeline, boolean hotMemeEnabled,
                                WritingStyleHints styleHints, NarrativeProfile narrativeProfile,
                                NarrativePhysicsMode narrativePhysicsMode) {
        long startTime = System.currentTimeMillis();
        String deAiFocus = NarrativeCraftPrompts.deAiNarrativeFocusBlock();
        if (isShuangwenPipeline(pipeline)) {
            deAiFocus = deAiFocus + "\n" + NarrativeCraftPrompts.deAiShuangwenPreserveBlock();
        } else {
            String deAiPipe = NarrativeCraftPrompts.deAiPipelinePreserveBlock(pipeline);
            if (!deAiPipe.isBlank()) {
                deAiFocus = deAiFocus + "\n" + deAiPipe;
            }
        }
        if (hotMemeEnabled) {
            deAiFocus = deAiFocus + "\n" + NarrativeCraftPrompts.deAiHotMemeTrimBlock();
        }
        String microDeAi = NarrativeCraftPrompts.styleMicroParamsBlock(styleHints);
        if (!microDeAi.isBlank()) {
            deAiFocus = deAiFocus + "\n" + microDeAi;
        }
        if (narrativeEngineEnabled && narrativeProfile != null) {
            NarrativePhysicsMode physics = narrativePhysicsMode != null
                    ? narrativePhysicsMode
                    : NarrativePhysicsMode.fromPipeline(pipeline);
            deAiFocus = deAiFocus + NarrativeCraftPrompts.narrativeEngineDeAiReminder(narrativeProfile, pipeline, physics);
        }
        String prompt = String.format("""
            你是资深小说统稿编辑。请在不改变剧情事实的前提下，消除 AI 痕迹并保持文风统一。

            【目标章节】第%d章
            【文风流水线】%s

            【去AI味要求】
            1. 删除模板化总结句、口号式升华、机械排比。
            2. 保留关键剧情与角色关系，不新增设定。
            3. 避免反复使用相同句式（如“他以为……他错了”）。
            4. 保持自然表达和人物个性化对白。
            %s

            【原文】
            %s

            请返回最终正文，不要解释。
            """, chapterNumber, pipeline == null ? WritingPipeline.POWER_FANTASY.name() : pipeline.name(), deAiFocus, content);
        return callAi(prompt, startTime, "终稿去AI味");
    }

    /** 默认流水线与 {@link WritingPipeline#POWER_FANTASY} 视为爽文网文向。 */
    private static boolean isShuangwenPipeline(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return p == WritingPipeline.POWER_FANTASY;
    }

    public String generateChapterSidecar(String chapterContent, String outline, int chapterNumber, WritingPipeline pipeline) {
        long startTime = System.currentTimeMillis();
        String prompt = String.format("""
            你是小说结构化分析器。请基于正文提取结构化 sidecar，并且只返回 JSON。

            【章节】第%d章
            【文风流水线】%s
            【大纲参考】
            %s

            【正文】
            %s

            只返回如下 JSON 结构，不要任何解释：
            {
              "title": "章节标题，尽量简短",
              "entities": ["本章关键角色名"],
              "facts": ["本章关键事实，短句，3-8条"],
              "continuity_anchor": "与上一章/下一章衔接的关键锚点"
            }
            """, chapterNumber, pipeline == null ? WritingPipeline.POWER_FANTASY.name() : pipeline.name(), outline, chapterContent);
        return callAi(prompt, startTime, "章节sidecar提取");
    }

    private String styleGuide(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case LIGHT_NOVEL -> """
                    文风目标：轻小说向。
                    - 侧重角色互动、轻快对白、情绪细腻、章节钩子柔和。
                    - 避免过于沉重的世界设定堆砌，强调可读性与角色魅力。
                    """;
            case SLICE_OF_LIFE -> """
                    文风目标：日常向。
                    - 侧重生活细节、关系推进、微冲突与温和节奏。
                    - 降低战力系统和宏大叙事比重，以人物日常成长为主。
                    """;
            case PERIOD_DRAMA -> """
                    文风目标：年代文（现实质感 + 时代气息）。
                    - 强化时代细节：物资稀缺、票证/单位/邻里关系、人情往来、口号与宣传语的真实使用场景（少而准）。
                    - 场景要有烟火气：吃穿住行、工作分配、家庭结构、邻里冲突与互助，细节必须具体可感。
                    - 情绪表达克制而有后劲：少空泛煽情，多用动作与生活压力推动人物选择。
                    - 冲突更偏“现实压迫/制度规则/人情秩序”而非纯战力碾压；爽点来自翻身、争取、守住体面与尊严。
                    """;
            case VULGAR -> """
                    文风目标：粗俗风（更口语、更江湖气、更带刺的幽默）。
                    - 允许脏话式语气与市井俚语，但避免露骨色情描写、仇恨歧视用语与恶意人身攻击。
                    - 句子更短、更狠：吐槽、插科打诨、嘴上不饶人，但行动要讲逻辑与代价。
                    - 对话要像“人吵架/人聊天”，可以打断、顶嘴、反问、冷笑；少文绉绉长段说教。
                    - 爽点来自反击与翻盘，但不要靠降智与机械降神。
                    """;
            default -> """
                    文风目标：爽文主线（通用网文，偏舒坦读感）。
                    - 事件驱动：每章尽量让读者看清「阻碍—应对—结果」，结果可以是**小胜、脱身、拿到筹码、扳回体面**，不必依赖高频当众打脸。
                    - 主角体验：可以受压，但避免连绵的公开羞辱与为虐而虐；憋屈要有代偿（机灵应对、底线、即时小翻盘或清晰反击预期）。
                    - 节奏：强钩子开场，冲突有度；少用分隔线与碎片化短段凑合转折。
                    - 对白：利落推进剧情；反派智力在线，少谜语演讲但也不必全员小丑化。
                    - 克制 AI 腔：少用「不是…是…」对立定义句与空洞补丁句；比喻适可而止。
                    - 战力与动机须可信：禁止无脑机械降神（与本篇叙事硬约束一致）。
                    """;
        };
    }
}
