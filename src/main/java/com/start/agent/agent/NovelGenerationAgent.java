package com.start.agent.agent;

import com.start.agent.model.WritingPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NovelGenerationAgent {

    private final ChatClient chatClient;
    private final ContentReviewAgent reviewAgent;
    private final PolishingAgent polishingAgent;
    private final ConsistencyReviewAgent consistencyAgent;

    public NovelGenerationAgent(
            ChatClient.Builder chatClientBuilder,
            ContentReviewAgent reviewAgent,
            PolishingAgent polishingAgent,
            ConsistencyReviewAgent consistencyAgent) {
        this.chatClient = chatClientBuilder.build();
        this.reviewAgent = reviewAgent;
        this.polishingAgent = polishingAgent;
        this.consistencyAgent = consistencyAgent;
        log.info("【AI代理初始化】NovelGenerationAgent 已就绪（生态型AI - 因果驱动版）");
    }

    public String generateOutline(String topic, String generationSetting, WritingPipeline pipeline) {
        log.info("【🤖 AI调用】开始生成故事大纲 - 题材: {}, 设定长度: {}", topic, textLength(generationSetting));
        long startTime = System.currentTimeMillis();
        String settingBlock = buildSettingBlock(generationSetting);

        String prompt = String.format("""
            你是一位极具创意的全能型作家，擅长各种类型的小说创作。

            请根据题材"%s"创作一个新颖独特、符合题材特色的故事大纲。

            %s

            【核心要求】
            1. 理解题材特性，不要把所有题材都写成传统玄幻/修仙。
            2. 根据题材构建世界观、主角身份、核心矛盾、力量体系或特殊机制。
            3. 人物要立体真实，配角有独立目标，对立角色要有合理理念。
            4. 至少规划15个章节的核心剧情，每章包含核心事件、冲突、情感/爽点、伏笔和节奏。
            5. 使用第三人称叙述，严禁第一人称。
            6. 必须优先遵守用户提供的设定，不能与设定冲突；设定不足处再自由发挥。

            【文风流水线】
            %s

            【输出格式】
            - 世界观设定
            - 主角设定
            - 主要角色
            - 对立角色
            - 剧情规划
            - 写作风格要求
            """, topic, settingBlock, styleGuide(pipeline));

        return callAi(prompt, startTime, "大纲生成");
    }

    public String generateChapter(String outline, int chapterNumber, String previousContent, String nextContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting, String immutableConstraints,
                                  WritingPipeline pipeline) {
        log.info("【📝 生态型AI】开始第{}章创作（因果驱动模式）", chapterNumber);
        long totalStartTime = System.currentTimeMillis();

        try {
            log.info("【步骤1/5】调用创作Agent生成初稿...");
            String draft = generateDraft(outline, chapterNumber, previousContent, nextContent, characterProfile, novelSetting, chapterSetting, immutableConstraints, pipeline);
            log.info("【步骤1/5】✅ 初稿生成成功，长度: {} 字符", draft.length());

            log.info("【步骤2/5】调用一致性审查Agent...");
            String consistencyProfile = mergeSettings(characterProfile, novelSetting, chapterSetting);
            String consistentContent = safeStep(
                    "一致性审查",
                    () -> consistencyAgent.reviewConsistency(draft, outline, consistencyProfile, chapterNumber, previousChaptersSummary),
                    draft
            );
            log.info("【步骤2/5】✅ 一致性审查完成，长度: {} 字符", consistentContent.length());

            log.info("【步骤3/5】调用审核Agent进行内容审查...");
            String reviewedContent = safeStep("内容审查", () -> reviewAgent.reviewAndFix(consistentContent), consistentContent);
            log.info("【步骤3/5】✅ 内容审核完成，长度: {} 字符", reviewedContent.length());

            log.info("【步骤4/5】调用润色Agent进行文笔优化...");
            String polishingOutline = mergeSettings(outline, novelSetting, chapterSetting);
            String polishedContent = safeStep(
                    "文笔润色",
                    () -> polishingAgent.polish(reviewedContent, polishingOutline + "\n\n【文风流水线】\n" + styleGuide(pipeline), chapterNumber),
                    reviewedContent
            );
            log.info("【步骤4/5】✅ 润色优化完成，长度: {} 字符", polishedContent.length());

            log.info("【步骤5/5】进行最终内容审核...");
            String reviewedFinalContent = safeStep("最终审核", () -> reviewAgent.reviewAndFix(polishedContent), polishedContent);
            String finalContent = safeStep("终稿去AI味", () -> deAiFinalize(reviewedFinalContent, chapterNumber, pipeline), reviewedFinalContent);
            log.info("【步骤5/5】✅ 最终审核完成，长度: {} 字符", finalContent.length());

            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.info("【🎉 创作完成】第{}章创作流程结束，总耗时: {}ms", chapterNumber, totalElapsed);
            return finalContent;
        } catch (Exception e) {
            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.error("【❌ 创作失败】第{}章创作异常 - 耗时: {}ms", chapterNumber, totalElapsed, e);
            throw e;
        }
    }

    private String generateDraft(String outline, int chapterNumber, String previousContent, String nextContent,
                                 String characterProfile, String novelSetting, String chapterSetting,
                                 String immutableConstraints, WritingPipeline pipeline) {
        log.info("【🤖 创作Agent】开始生成第{}章初稿", chapterNumber);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位精通网文节奏的顶级作家，采用"生态型AI"创作模式。

            【重要要求】
            - 必须使用第三人称叙述，严禁使用第一人称。
            - 不要出现"本章完""未完待续"等字样。
            - 所有事件必须有明确的因果来源，不得凭空捏造巧合事件。
            - 信息揭露必须符合延迟释放机制（每章最多揭露一个层级）。
            - 人物行为必须符合其目标/恐惧/知识状态。

            【故事大纲】
            %s

            【角色设定档案】
            %s

            %s

            %s

            %s

            【文风流水线】
            %s

            【上一章内容回顾】
            %s

            【下一章已存在内容（若为中间重生必须兼容）】
            %s

            【本章创作结构】
            1. 钩子：开篇给出危机、异常、压迫、误解或悬念。
            2. 压制：制造情绪下沉，让主角面对困难或不公平。
            3. 反转触发：通过能力、信息、选择或人物关系产生反转。
            4. 高潮爆发：让冲突集中释放，节奏加快，情绪拉满。
            5. 余震：留下后续悬念，不要总结人生意义。

            【写作风格】
            - 总字数 2000-3000 字。
            - 长短句交错，允许情绪断裂和风格突变。
            - 加入1-2个真实生活细节。
            - 对话自然，人物行为符合设定但允许合理的矛盾和复杂性。
            - 严格保持核心角色名称稳定，不新增与核心角色高度相似的新名字。
            - 必须与上一章形成因果承接；若提供了下一章内容，本章结尾要自然过渡到下一章已发生事件。
            - 中间章节重生时，严禁推翻下一章已确定的关键事实（人物生死、地点、关键道具归属、阵营关系）。

            现在开始创作第%d章：
            """, outline, characterProfile, buildImmutableConstraintsBlock(immutableConstraints), buildSettingBlock(novelSetting), buildChapterSettingBlock(chapterSetting), styleGuide(pipeline), previousContent, buildNextChapterBlock(nextContent), chapterNumber);

        return callAi(prompt, startTime, "第" + chapterNumber + "章初稿生成（生态型）");
    }

    public String generateCharacterProfile(String topic, String generationSetting, WritingPipeline pipeline) {
        log.info("【🤖 AI调用】开始生成角色设定 - 题材: {}, 设定长度: {}", topic, textLength(generationSetting));
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位专业的角色设计师，擅长为各种题材创造立体丰满的角色。

            请为题材"%s"创作符合题材特色、立体真实的角色档案。

            %s

            【要求】
            1. 必须优先遵守用户设定，角色身份、性格、关系、世界观不能与用户设定冲突。
            2. 主角要包含基本信息、外貌、性格、背景故事、特殊能力/身份/处境、行为模式、人际关系、内在矛盾。
            3. 设计2-3个重要配角，每个配角包含目标、作用、互动张力和成长弧线。
            4. 设计1-2个对立角色，包含理念、冲突点、个人魅力、原则和底线。
            5. 给出角色互动关系图和叙述视角说明。
            6. 使用第三人称叙述说明。

            【文风流水线】
            %s

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
                  "type": "主角/配角/反派",
                  "want": "...",
                  "fear": "...",
                  "knowledge": "...",
                  "summary": "简短角色简介"
                }
              ]
            }
            严禁输出 markdown 代码块、解释文本、前后缀说明、额外字段。
            """, topic, buildSettingBlock(generationSetting), styleGuide(pipeline));

        return callAi(prompt, startTime, "角色设定生成");
    }

    private String callAi(String prompt, long startTime, String actionName) {
        log.debug("【🤖 AI调用】{}提示词长度: {} 字符", actionName, prompt.length());
        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【🤖 AI调用】✅ {}成功 - 耗时: {}ms, 结果长度: {} 字符", actionName, elapsed, result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【🤖 AI调用】❌ {}失败 - 耗时: {}ms", actionName, elapsed, e);
            throw e;
        }
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

    private String buildNextChapterBlock(String nextContent) {
        if (nextContent == null || nextContent.trim().isEmpty()) {
            return "无。";
        }
        String normalized = nextContent.trim();
        return normalized.length() <= 1500 ? normalized : normalized.substring(0, 1500) + "\n...(已截断)";
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

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    @FunctionalInterface
    private interface ChapterStep {
        String run() throws Exception;
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

    private String deAiFinalize(String content, int chapterNumber, WritingPipeline pipeline) {
        long startTime = System.currentTimeMillis();
        String prompt = String.format("""
            你是资深小说统稿编辑。请在不改变剧情事实的前提下，消除 AI 痕迹并保持文风统一。

            【目标章节】第%d章
            【文风流水线】%s

            【去AI味要求】
            1. 删除模板化总结句、口号式升华、机械排比。
            2. 保留关键剧情与角色关系，不新增设定。
            3. 避免反复使用相同句式（如“他以为……他错了”）。
            4. 保持自然表达和人物个性化对白。

            【原文】
            %s

            请返回最终正文，不要解释。
            """, chapterNumber, pipeline.name(), content);
        return callAi(prompt, startTime, "终稿去AI味");
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
            default -> """
                    文风目标：爽文主线。
                    - 保留强冲突、强节奏、反转与高能场面。
                    - 但避免机械爽点堆叠，保持人物动机可信。
                    """;
        };
    }
}
