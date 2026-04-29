
package com.start.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PolishingAgent {

    private final ChatClient chatClient;

    public PolishingAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("【AI代理初始化】PolishingAgent 已就绪 (去AI味破坏者模式)");
    }

    public String polish(String content, String outline, int chapterNumber) {
        log.info("【✨ 润色优化】开始对第{}章进行“去AI味”深度改造", chapterNumber);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
            你是一位极具个性的资深网文主编，你的核心任务不是“润色”，而是**“破坏”**。
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
               - 例子：他明知道那是错的，但还是点了确认。不是冲动。他只是突然不想当一个对的人了。
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
            
            【执行策略】
            - 不要改变核心剧情和爽点结构。
            - **删掉 20%% 的废话句子**。
            - **改掉 30%% 的工整句式**。
            - **加入 10%% 的“无用细节”**。
            - 保持第三人称叙述。
            
            请返回改造后的完整内容，让它看起来**不像 AI 写的**：
            """, outline, chapterNumber, content);

        log.debug("【✨ 润色优化】提示词长度: {} 字符", prompt.length());

        try {
            String result = chatClient.prompt(prompt).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("【✨ 润色优化】✅ “去AI味”改造完成 - 耗时: {}ms, 结果长度: {} 字符", elapsed, result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【✨ 润色优化】❌ 改造失败 - 耗时: {}ms", elapsed, e);
            throw e;
        }
    }
}
