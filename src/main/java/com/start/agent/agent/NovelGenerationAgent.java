package com.start.agent.agent;

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
        log.info("【AI代理初始化】NovelGenerationAgent 已就绪 (多Agent协作模式 - 设定增强版)");
    }

    public String generateOutline(String topic, String generationSetting) {
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

            【输出格式】
            - 世界观设定
            - 主角设定
            - 主要角色
            - 对立角色
            - 剧情规划
            - 写作风格要求
            """, topic, settingBlock);

        return callAi(prompt, startTime, "大纲生成");
    }

    public String generateChapter(String outline, int chapterNumber, String previousContent,
                                  String characterProfile, String previousChaptersSummary,
                                  String novelSetting, String chapterSetting) {
        log.info("【📝 多Agent协作】开始第{}章创作，小说设定长度: {}，本章设定长度: {}",
                chapterNumber, textLength(novelSetting), textLength(chapterSetting));
        long totalStartTime = System.currentTimeMillis();

        try {
            log.info("【步骤1/5】调用创作Agent生成初稿...");
            String draft = generateDraft(outline, chapterNumber, previousContent, characterProfile, novelSetting, chapterSetting);
            log.info("【步骤1/5】✅ 初稿生成成功，长度: {} 字符", draft.length());

            log.info("【步骤2/5】调用一致性审查Agent...");
            String consistencyProfile = mergeSettings(characterProfile, novelSetting, chapterSetting);
            String consistentContent = consistencyAgent.reviewConsistency(
                    draft, outline, consistencyProfile, chapterNumber, previousChaptersSummary);
            log.info("【步骤2/5】✅ 一致性审查完成，长度: {} 字符", consistentContent.length());

            log.info("【步骤3/5】调用审核Agent进行内容审查...");
            String reviewedContent = reviewAgent.reviewAndFix(consistentContent);
            log.info("【步骤3/5】✅ 内容审核完成，长度: {} 字符", reviewedContent.length());

            log.info("【步骤4/5】调用润色Agent进行文笔优化...");
            String polishingOutline = mergeSettings(outline, novelSetting, chapterSetting);
            String polishedContent = polishingAgent.polish(reviewedContent, polishingOutline, chapterNumber);
            log.info("【步骤4/5】✅ 润色优化完成，长度: {} 字符", polishedContent.length());

            log.info("【步骤5/5】进行最终内容审核...");
            String finalContent = reviewAgent.reviewAndFix(polishedContent);
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

    private String generateDraft(String outline, int chapterNumber, String previousContent,
                                 String characterProfile, String novelSetting, String chapterSetting) {
        log.info("【🤖 创作Agent】开始生成第{}章初稿", chapterNumber);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位精通网文节奏的顶级作家。

            【重要要求】
            - 必须使用第三人称叙述，严禁使用第一人称。
            - 不要出现“本章完”“未完待续”等字样。
            - 保持与上一章、故事大纲、角色设定、用户设定一致。
            - 如果本章设定与小说全局设定不冲突，优先落实本章设定。

            【故事大纲】
            %s

            【角色设定档案】
            %s

            %s

            %s

            【上一章内容回顾】
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

            现在开始创作第%d章：
            """, outline, characterProfile, buildSettingBlock(novelSetting), buildChapterSettingBlock(chapterSetting), previousContent, chapterNumber);

        return callAi(prompt, startTime, "第" + chapterNumber + "章初稿生成");
    }

    public String generateCharacterProfile(String topic, String generationSetting) {
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

            请直接输出角色档案：
            """, topic, buildSettingBlock(generationSetting));

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

    private String mergeSettings(String base, String novelSetting, String chapterSetting) {
        StringBuilder builder = new StringBuilder();
        if (base != null && !base.trim().isEmpty()) {
            builder.append(base.trim()).append("\n\n");
        }
        builder.append(buildSettingBlock(novelSetting)).append("\n\n");
        builder.append(buildChapterSettingBlock(chapterSetting));
        return builder.toString();
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }
}
