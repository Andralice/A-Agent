package com.start.agent.agent;


import com.start.agent.prompt.NarrativeCraftPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsistencyReviewAgent {

    private final ChatClient chatClient;

    public ConsistencyReviewAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("【AI代理初始化】ConsistencyReviewAgent 已就绪 (创新保护版)");
    }

    public String reviewConsistency(String content, String outline, String characterProfile, 
                                     int chapterNumber, String previousChaptersSummary) {
        log.info("【🔍 一致性审查】开始审查第{}章内容的一致性", chapterNumber);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位专业的网文主编，负责审查小说章节是否符合整体设定和大纲，
            同时要保护和鼓励创新性，避免过度标准化导致内容平庸。
            
            【故事大纲】
            %s
            
            【角色设定档案】
            %s
            
            【前情提要】（之前章节的关键剧情和伏笔）
            %s
            
            【当前章节】第%d章
            【待审查内容】
            %s
            
            【审查要点】
            
            1. **大纲符合度**：
               - 本章剧情是否在大纲规划范围内
               - 是否偏离了主线剧情
               - 如果有创新性的改编，评估是否更好
            
            2. **角色一致性**：
               - 主角的性格是否与设定一致
               - 角色的行为是否符合其身份和背景
               - 角色的能力水平是否合理（不能突然变强或变弱）
               - 对话风格是否符合角色特点
               - 允许角色有成长和变化，但要合理
            
            3. **设定连贯性**：
               - 世界观设定是否前后一致（修炼体系、力量等级等）
               - 之前提到的伏笔是否有呼应
               - 之前出现的人物、物品、地点是否保持一致
               - 时间线是否合理
            
            4. **逻辑合理性**：
               - 情节发展是否有逻辑漏洞
               - 因果关系是否成立
               - 角色的动机是否合理
               - 事件的后果是否符合常理
            
            5. **连续性检查**：
               - 与上一章的衔接是否自然
               - 人物的状态、位置、情绪是否连贯
               - 未完成的事件是否得到处理
            
            6. **创新性保护**（非常重要）：
               - 如果内容有新颖的设计，不要轻易否定
               - 评估创新性是否提升了作品质量
               - 只有在确实违背设定时才修改
               - 鼓励独特的叙事角度和表达方式
            %s
            
            【处理方式】
            
            如果发现不一致的地方：
            1. 轻微问题：直接修正内容，保持原文风格
            2. 严重偏离：调整剧情使其回归大纲轨道
            3. 角色OOC（性格偏移）：修正对话和行为使其符合设定
            4. 设定冲突：统一设定，消除矛盾
            
            如果是创新性的改编：
            1. 评估是否比原大纲更好
            2. 如果更好，保留并记录这个改动
            3. 如果不好，温和地调整回原轨道
            4. 不要扼杀有价值的创新
            
            【要求】
            1. 如果内容完全符合要求，直接返回原文
            2. 如果有需要修改的地方，返回修正后的完整内容
            3. 保持原文的风格、节奏和字数
            4. 不要添加任何说明或注释
            5. 重点保护已建立的伏笔和人物关系
            6. 尊重作者的创新意图，不要过度标准化
            
            请返回审查修正后的内容：
            """, outline, characterProfile, previousChaptersSummary, chapterNumber, content,
                NarrativeCraftPrompts.consistencyReviewNarrativeQualityBlock());

        log.debug("【🔍 一致性审查】提示词长度: {} 字符", prompt.length());

        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【🔍 一致性审查】✅ 审查完成 - 耗时: {}ms, 结果长度: {} 字符", elapsed, result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【🔍 一致性审查】❌ 审查失败 - 耗时: {}ms", elapsed, e);
            throw e;
        }
    }

    public String generateChapterSummary(String content, int chapterNumber) {
        log.info("【📝 摘要生成】开始生成第{}章的剧情摘要", chapterNumber);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位专业的编辑助手，负责为小说章节生成简洁的剧情摘要。
            
            【当前章节】第%d章
            
            【章节内容】
            %s
            
            【要求】
            请生成一个200-300字的剧情摘要，包含：
            1. 本章发生的核心事件
            2. 重要的人物互动和关系变化
            3. 新出现的伏笔或线索
            4. 主角的实力进展或收获
            5. 结尾的悬念或转折
            
            注意：
            - 只提取关键信息，不要详细描述
            - 保留重要的人名、地名、物品名
            - 突出对后续剧情有影响的内容
            - 使用第三人称叙述
            
            请直接返回摘要内容：
            """, chapterNumber, content);

        log.debug("【📝 摘要生成】提示词长度: {} 字符", prompt.length());

        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【📝 摘要生成】✅ 摘要生成完成 - 耗时: {}ms, 结果长度: {} 字符", elapsed, result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【📝 摘要生成】❌ 摘要生成失败 - 耗时: {}ms", elapsed, e);
            throw e;
        }
    }
}
