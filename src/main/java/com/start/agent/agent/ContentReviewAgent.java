package com.start.agent.agent;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内容安全与合规审查 Agent：对正文做风险扫描与必要修补（偏政策与尺度）。
 */
@Slf4j
@Component
public class ContentReviewAgent {

    private final ChatClient chatClient;

    /** 审核结果低于输入字符数的比例时视为模型未返回正文（仅输出评语），退回原文。 */
    @Value("${novel.llm.review-min-length-ratio:0.5}")
    private double reviewMinLengthRatio;

    public ContentReviewAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("【AI代理初始化】ContentReviewAgent 已就绪");
    }

    public String reviewAndFix(String content) {
        int inputLen = content == null ? 0 : content.length();
        log.info("【🔍 内容审核】开始审核内容，长度: {} 字符", inputLen);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位专业的网文内容审核编辑，负责审查小说内容是否符合平台规范。

            【审核标准】
            1. **政治敏感**：不得涉及真实政治人物、事件、制度批评
            2. **色情低俗**：不得有露骨性描写、性暗示、淫秽语言
            3. **暴力血腥**：不得有过度的血腥场面、残忍描写、恐怖内容
            4. **违法内容**：不得宣扬犯罪、毒品、赌博等违法行为
            5. **价值观**：不得传播消极、反社会、歧视性观点
            6. **宗教民族**：不得侮辱宗教信仰、民族情感

            【处理方式】
            - 发现敏感内容时，用委婉、隐晦的方式改写
            - 保持原文的剧情走向和爽点
            - 暴力场面改为"气势碰撞"、"能量激荡"等抽象描述
            - 色情暗示改为情感交流、心灵契合等含蓄表达
            - 政治隐喻改为虚构的宗门、势力斗争

            【待审核内容】
            %s

            【要求】
            1. 如果内容完全合规，直接返回原文的**完整正文**（不要只写「内容合规」，必须把原文一字不差地返回）
            2. 如果有需要修改的地方，返回修改后的完整内容
            3. 保持原文的风格、节奏和字数
            4. 不要添加任何说明或注释

            请返回审核后的完整正文：
            """, content);

        log.debug("【🔍 内容审核】提示词长度: {} 字符", prompt.length());

        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            int resultLen = result == null ? 0 : result.length();
            double ratio = inputLen > 0 ? (double) resultLen / inputLen : 0;
            if (resultLen > 0 && ratio < clampReviewMinRatio(reviewMinLengthRatio)) {
                log.warn("【🔍 内容审核】⚠️ 模型疑似仅返回评语而未返回正文，退回原文 — 输入 {} 字符，结果 {} 字符（比例 {:.2f} < {:.2f}）",
                        inputLen, resultLen, ratio, clampReviewMinRatio(reviewMinLengthRatio));
                return content;
            }
            log.info("【🔍 内容审核】✅ 审核完成 - 耗时: {}ms, 结果长度: {} 字符", elapsed, resultLen);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【🔍 内容审核】❌ 审核失败 - 耗时: {}ms", elapsed, e);
            throw e;
        }
    }

    private static double clampReviewMinRatio(double raw) {
        return Math.max(0.2, Math.min(0.9, raw));
    }
}
