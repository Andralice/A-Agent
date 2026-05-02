
package com.start.agent.agent;

import com.start.agent.model.WritingPipeline;
import com.start.agent.prompt.NarrativeCraftPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 文笔润色 Agent：在合规前提下优化行文节奏与可读性，并按流水线切换文风提示。
 */
@Slf4j
@Component
public class PolishingAgent {

    private final ChatClient chatClient;

    public PolishingAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("【AI代理初始化】PolishingAgent 已就绪 (去AI味破坏者模式)");
    }

    public String polish(String content, String outline, int chapterNumber) {
        return polish(content, outline, chapterNumber, WritingPipeline.POWER_FANTASY);
    }

    /** 爽文（POWER_FANTASY）走节奏保真润色；其它流水线保留破坏式去 AI 味。 */
    public String polish(String content, String outline, int chapterNumber, WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        boolean shuangwen = p == WritingPipeline.POWER_FANTASY;
        log.info("【✨ 润色优化】第{}章 — {}", chapterNumber, shuangwen ? "爽文节奏润色" : "去AI味深度改造");
        long startTime = System.currentTimeMillis();

        String prompt = shuangwen ? buildShuangwenPolishPrompt(outline, chapterNumber, content)
                : buildDestructivePolishPrompt(outline, chapterNumber, content);

        log.debug("【✨ 润色优化】提示词长度: {} 字符", prompt.length());

        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【✨ 润色优化】✅ 完成 - 耗时: {}ms, 结果长度: {} 字符", elapsed, result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【✨ 润色优化】❌ 失败 - 耗时: {}ms", elapsed, e);
            throw e;
        }
    }

    private static String buildShuangwenPolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是资深网文主编，擅长爽文的「一口气读完」与节奏兑现（不必依赖高频打脸）。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待润色正文】
            %s
            
            【爽文润色 — 必须遵守】
            1. **保进展**：不改剧情事实与结局走向；本章「施压—应对—局面变化—得失」链条要保留。不必每场都是围观打脸；智斗、脱身、小胜、保全尊严同样算兑现。可删赘语，不可把关键破局收成一段空洞内心总结。
            2. **去杠杆**：删掉模板总结句、口号式升华、机械排比；少用「不是X，是Y」对立定义句，能直说就直说。
            3. **反派效率**：压缩纯演讲型独白；对话短、狠、推动下一步动作。
            4. **排版**：删除正文中的「---」、「# ...」标题行、「（本章完）」；分段只用空行。
            5. **节奏**：适度长短句即可，**禁止**整章做一次剧烈文风跳台（例如突然长篇脱口秀或荒诞黑色幽默接管叙事）。
            6. **补丁句**：压缩「沉默了很久」「某种更复杂的东西」「很难形容」这类空洞补丁；换成旁人一句嘴、价码、物证或场面变化。
            7. **战斗**：若「一震/一剑」空话定局，用最短两步因果或代价补丁（不加新设定、不脱纲）。
            8. **保趣味**：保留让读者「有反应」的对白锋芒与小高潮节拍；删套路总结即可，勿把全文磨成寡淡温水。
            
            %s
            
            【执行策略】
            - 删除明显废话约 15%%；收紧冗长描写约 10%%。
            - 保持第三人称。
            
            请返回润色后的完整正文：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendumShuangwen());
    }

    private static String buildDestructivePolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是一位极具个性的资深网文主编，你的核心任务是做一次**“破坏式重写”**。
            你要把 AI 生成的平庸、工整、正确的文字，改造成有血有肉、有偏差、有噪音的人类作品。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待改造内容】
            %s
            
            【核心改造指令 - 必须严格执行】
            
            1️⃣ **打破“情绪恒温” (强制情绪断裂)**
               - ❌ 禁止：他很难过，但坚持完成了任务。
               - ✅ 必须：前半段极端情绪（崩溃/愤怒/荒诞），后半段突然冷静甚至冷漠。
               - 例子：他在厕所吐到眼泪混着胃酸，十分钟后，他洗了脸，像什么都没发生一样去签字。
               - **行动**：找出文中情绪平稳的地方，制造“断裂感”。
            
            2️⃣ **句式“长短失控” (节奏跛脚)**
               - ❌ 禁止：平均长度的句子，结构对称，逻辑完整。
               - ✅ 必须：一段里塞一个超长句（甚至带点混乱），下一句突然极短。
               - 例子：他解释了很多，从动机到过程，从误会到命运，像是在替自己辩护。没人听。包括他自己。
               - **行动**：打碎工整的句式，制造节奏的“跛脚感”。
            
            3️⃣ **插入“无用但真实”的细节 (真实感爆炸)**
               - ❌ 禁止：房间很凌乱，桌上堆满文件。（服务剧情的细节）
               - ✅ 必须：桌上有一杯三天前的咖啡，表面已经长出一层薄薄的灰。（对剧情没用但极真实）
               - **行动**：在场景描写中，强行插入 1-2 个与主线无关但极具生活质感的细节。
            
            4️⃣ **允许人物“不合理” (自我背叛)**
               - ❌ 禁止：人物太讲道理，太一致，太“正确”。
               - ✅ 必须：让人物突然说反话，做自己都解释不了的决定，前后矛盾。
               - 例子：他明知道那是错的，手指却还是按下去。像是跟自己较劲，又像是懒得再争辩。
               - **行动**：在人物决策时，加入一点“非理性”或“自我背叛”的心理描写。
            
            5️⃣ **风格突变 (突然换频道)**
               - ❌ 禁止：整篇风格统一。
               - ✅ 必须：正常叙述 -> 突然像聊天；正经描写 -> 突然黑色幽默；文艺 -> 突然粗糙。
               - 例子：他的人生像一条精心规划的轨道。然后他脱轨了。很响。很难看。
               - **行动**：在段落转换时，尝试一次风格的剧烈跳跃。
            
            6️⃣ **删掉“总结句” (悬空结尾)**
               - ❌ 禁止：这让他明白了人生的意义……（AI 最爱的总结）
               - ✅ 必须：直接删掉总结，换成“悬空句”。
               - 例子：他点了点头。至于为什么，他没有再想。
               - **行动**：检查每段结尾，删掉所有升华意义的句子，留给读者补完。

            7️⃣ **压制“对立定义句式” (减少 AI 腔转折)**
               - ❌ 禁止：为显得有力度而反复使用「不是X，是Y」「并非X，而是Y」「与其说X，不如说Y」。
               - ✅ 必须：能直说就直说；转折靠事件与后果；需要强调时用动作、细节、语气断裂来承载，不靠模板句式。
            
            %s
            
            【执行策略】
            - 不要改变核心剧情和爽点结构。
            - **删掉 20%% 的废话句子**。
            - **改掉 30%% 的工整句式**。
            - **加入 10%% 的“无用细节”**。
            - 保持第三人称叙述。
            
            请返回改造后的完整内容，让它看起来**不像 AI 写的**：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendum());
    }
}
