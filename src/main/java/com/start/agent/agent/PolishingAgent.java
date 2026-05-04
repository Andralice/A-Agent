
package com.start.agent.agent;

import com.start.agent.model.WritingPipeline;
import com.start.agent.narrative.NarrativeCriticReport;
import com.start.agent.narrative.NarrativeLintIssue;
import com.start.agent.narrative.NarrativeLintReport;
import com.start.agent.narrative.NarrativeProfile;
import com.start.agent.prompt.NarrativeCraftPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
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
        log.info("【AI代理初始化】PolishingAgent 已就绪（按 WritingPipeline 分支润色）");
    }

    public String polish(String content, String outline, int chapterNumber) {
        return polish(content, outline, chapterNumber, WritingPipeline.POWER_FANTASY);
    }

    /** 按 {@link WritingPipeline} 选用专用润色策略（爽文节奏保真 / 轻小说与日常轻度去皱 / 年代克制 / 粗俗短句）。 */
    public String polish(String content, String outline, int chapterNumber, WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String modeLabel = switch (p) {
            case POWER_FANTASY -> "爽文节奏润色";
            case LIGHT_NOVEL -> "轻小说润色";
            case SLICE_OF_LIFE -> "日常向润色";
            case PERIOD_DRAMA -> "年代文润色";
            case VULGAR -> "粗俗风润色";
        };
        log.info("【✨ 润色优化】第{}章 — {}", chapterNumber, modeLabel);
        long startTime = System.currentTimeMillis();

        String prompt = switch (p) {
            case POWER_FANTASY -> buildShuangwenPolishPrompt(outline, chapterNumber, content);
            case LIGHT_NOVEL -> buildLightNovelPolishPrompt(outline, chapterNumber, content);
            case SLICE_OF_LIFE -> buildSlicePolishPrompt(outline, chapterNumber, content);
            case PERIOD_DRAMA -> buildPeriodPolishPrompt(outline, chapterNumber, content);
            case VULGAR -> buildVulgarPolishPrompt(outline, chapterNumber, content);
        };

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

    /**
     * M3：叙事 Lint 命中后的窄幅修订——优先去掉禁止短语与标签句，不改剧情因果。
     */
    public String polishNarrativeLintPass(String content,
                                          int chapterNumber,
                                          WritingPipeline pipeline,
                                          NarrativeProfile profile,
                                          NarrativeLintReport report,
                                          double temperature,
                                          int maxTokens) {
        if (content == null || content.isBlank() || report == null || !report.hasIssues()) {
            return content;
        }
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        log.info("【叙事Lint修订】第{}章 — 命中 {} 条，pipeline={}", chapterNumber, report.issues().size(), p.name());
        long startTime = System.currentTimeMillis();

        StringBuilder issueBlock = new StringBuilder();
        for (NarrativeLintIssue i : report.issues()) {
            issueBlock.append("- [").append(i.type()).append("] ").append(i.detail()).append("\n");
        }

        String forbidReminder = "";
        if (profile != null && !profile.forbiddenLines().isEmpty()) {
            forbidReminder = "以下短语仍严禁出现（除非在对白里作为他人嘲讽且有必要）："
                    + String.join("、", profile.forbiddenLines()) + "\n\n";
        }

        double t = Math.max(0, Math.min(2, temperature));
        int mt = Math.max(512, Math.min(8192, maxTokens));

        String prompt = String.format("""
                你是小说窄范围修订编辑。【只允许】消除下列叙事 Lint 问题：替换为空洞标签的情绪句、命中禁止短语、过度直白的「点名情绪」。
                
                【硬性要求】
                1. **不改剧情**：人物生死、关键事实、道具归属、对立结构、章节结局走向一律不变。
                2. **可改写范围**：用语与句式；把标签情绪改成动作、对白、停顿、生理细节或他人可见反应。
                3. **禁止**：新增设定、新增核心角色、扩写无关桥段、写meta「本章完」。
                4. **排版**：保留分段习惯；不要引入 markdown 标题或「---」分隔线。
                5. **第三人称**与本书一致。
                
                【叙事引擎管线】%s
                
                【Lint 命中（须逐项处理；处理后正文不应再含有禁止短语的字面连续出现）】
                %s
                
                %s【待修订正文｜第%d章】
                %s
                
                请只输出修订后的完整正文，不要解释。
                """, p.name(), issueBlock.toString().trim(), forbidReminder, chapterNumber, content);

        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(t)
                .maxTokens(mt)
                .build();
        try {
            String result = chatClient.prompt(prompt).options(opts).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            if (result == null || result.isBlank()) {
                log.warn("【叙事Lint修订】✅ 返回空，保留原文 - 耗时: {}ms", elapsed);
                return content;
            }
            log.info("【叙事Lint修订】✅ 完成 - 耗时: {}ms, 长度 {} -> {}", elapsed, content.length(), result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【叙事Lint修订】❌ 失败 - 耗时: {}ms，保留原文", elapsed, e);
            return content;
        }
    }

    /**
     * 流体润滑：终稿去 AI 味之后、Lint 之前；只加强过渡与体感连续，不改剧情因果。
     */
    public String polishNarrativeFlowPass(String content,
                                          int chapterNumber,
                                          WritingPipeline pipeline,
                                          double temperature,
                                          int maxTokens) {
        if (content == null || content.isBlank()) {
            return content;
        }
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        log.info("【叙事流体润滑】第{}章 — pipeline={}", chapterNumber, p.name());
        long startTime = System.currentTimeMillis();
        double t = Math.max(0, Math.min(2, temperature));
        int mt = Math.max(512, Math.min(8192, maxTokens));

        String prompt = NarrativeCraftPrompts.narrativeFlowSmootherRole(p) + String.format("""


                【待润滑正文｜第%d章】
                %s

                请只输出润滑后的完整正文，不要解释。
                """, chapterNumber, content);

        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(t)
                .maxTokens(mt)
                .build();
        try {
            String result = chatClient.prompt(prompt).options(opts).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            if (result == null || result.isBlank()) {
                log.warn("【叙事流体润滑】返回空，保留原文 - 耗时: {}ms", elapsed);
                return content;
            }
            log.info("【叙事流体润滑】✅ 完成 - 耗时: {}ms, 长度 {} -> {}", elapsed, content.length(), result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【叙事流体润滑】❌ 失败 - 耗时: {}ms，保留原文", elapsed, e);
            return content;
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

    private static String buildLightNovelPolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是资深轻小说责编，擅长保留「对白节奏 + 角色魅力」，同时去掉 AI 腔与赘述。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待润色正文】
            %s
            
            【轻小说润色 — 必须遵守】
            1. **保剧情**：不改事实与结局；误会、约定、比赛、关系的进展链条要完整。
            2. **对白优先**：能用两句对白推进的，不写一段旁白解释；内心独白留一两句点睛即可。
            3. **去杠杆**：删说明书式设定堆砌、模板升华；少用对立定义句式。
            4. **排版**：去掉「---」「# 标题」「（本章完）」；分段用空行。
            5. **基调**：轻快但有内容，不把一章磨成白开水；禁止突然变成血腥虐杀纪录片腔（除非本章本就如此）。
            6. **第三人称**。
            
            %s
            
            【执行策略】
            - 删废话约 12%%；适度收紧描写；保留俏皮与停顿感。
            
            请返回润色后的完整正文：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendumLightNovel());
    }

    private static String buildSlicePolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是资深日常向小说编辑，擅长让生活流读起来舒服、可信，去掉说教与 AI 补丁腔。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待润色正文】
            %s
            
            【日常向润色 — 必须遵守】
            1. **保剧情**：不改人物关系与事件结果；小事也要有因果。
            2. **生活质感**：保留或补强一两处具体细节（路况、吃食、短信、小钱），删空洞形容词。
            3. **情绪**：少用标签句「她很失落」；改用动作、沉默、话到嘴边又咽下。
            4. **去杠杆**：删鸡汤总结、机械排比；转折靠事件，不靠旁白讲道理。
            5. **勿玄幻化**：非异能题材不要润色成开挂打脸口吻。
            6. **排版**：去掉 markdown 分隔线与标题行。
            7. **第三人称**。
            
            %s
            
            【执行策略】
            - 删空话约 12%%；节奏舒缓但有波纹。
            
            请返回润色后的完整正文：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendumSliceOfLife());
    }

    private static String buildPeriodPolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是资深年代文主编，擅长克制、烟火气与时代可信感；禁止把文字改成段子或网游腔。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待润色正文】
            %s
            
            【年代文润色 — 必须遵守】
            1. **保剧情**：不改走向与人物立场；压迫与翻盘要有生活逻辑。
            2. **时代语感**：删掉网络流行语、二次元梗、违和的现代企业管理腔；用语贴合人物身份。
            3. **情绪克制**：外溢靠细节与省略，不靠咆哮式排比；禁止为「生动」制造荒诞幽默断层。
            4. **去杠杆**：删模板升华与对立定义堆砌。
            5. **排版**：去掉「---」「# ...」「（本章完）」。
            6. **第三人称**。
            
            %s
            
            【执行策略】
            - 删赘述约 10%%；宁可朴素也要可信。
            
            请返回润色后的完整正文：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendumPeriodDrama());
    }

    private static String buildVulgarPolishPrompt(String outline, int chapterNumber, String content) {
        return String.format("""
            你是资深市井题材编辑，擅长短句、狠对白与江湖气，同时守住合规底线（无色情细节、无仇恨煽动）。
            
            【故事大纲背景】
            %s
            
            【当前章节】第%d章
            
            【待润色正文】
            %s
            
            【粗俗风润色 — 必须遵守】
            1. **保剧情**：不改冲突结果；嘴炮要与后果对齐。
            2. **短句**：对线利落，删讲解员式旁白；多人对话要有抢话与打断。
            3. **粗口**：可精炼使用语气攻击性，禁止堆砌脏字刷屏；绝不写出露骨性行为描写。
            4. **去杠杆**：删对称排比与「人生导师」式总结。
            5. **排版**：去掉 markdown 痕迹。
            6. **第三人称**。
            
            %s
            
            【执行策略】
            - 删废话约 15%%；节奏偏快但有落脚点。
            
            请返回润色后的完整正文：
            """, outline, chapterNumber, content, NarrativeCraftPrompts.polishingCraftAddendumVulgar());
    }

    private static final int M8_CONTENT_CLIP = 28_000;
    private static final int M8_OUTLINE_CLIP = 2_500;

    /**
     * M8：叙事批评 pass，模型须只输出 JSON：{@code {"issues":[{"severity":"low|medium|high","detail":"..."}]}}。
     */
    public String callNarrativeCritic(String content,
                                      String outline,
                                      int chapterNumber,
                                      WritingPipeline pipeline,
                                      NarrativeProfile profile,
                                      double temperature,
                                      int maxTokens) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String body = clipForPrompt(content, M8_CONTENT_CLIP);
        String outlineClip = clipForPrompt(outline == null ? "" : outline, M8_OUTLINE_CLIP);
        String profileHint = profile == null ? "（无叙事 profile）" : profile.toLogSummary();
        double t = Math.max(0, Math.min(1.5, temperature));
        int mt = Math.max(256, Math.min(4096, maxTokens));

        String prompt = String.format("""
                你是小说技术审读编辑。只做结构层面的「挑刺」，不替作者重写正文。
                请**只输出一个 JSON 对象**，不要 markdown 代码围栏，不要前后解释文字。
                严格符合此结构（键名必须一致）：
                {"issues":[{"severity":"low|medium|high","detail":"不超过90字的中文"}]}
                若无问题则输出 {"issues":[]}。
                最多 12 条，按严重度从高到低排列；关注：节奏拖沓、信息重复交代、动机不清、转折过陡、对白说明书化、与大纲明显矛盾（若能从大纲判断）。
                %s
                
                【叙事管线】%s
                【叙事 profile 摘要】%s
                
                【大纲摘录】
                %s
                
                【第%d章正文】
                %s
                """, NarrativeCraftPrompts.narrativeM8CriticTooSmoothInstructions(), p.name(), profileHint, outlineClip, chapterNumber, body);

        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(t)
                .maxTokens(mt)
                .build();
        String result = chatClient.prompt(prompt).options(opts).call().content();
        return result == null ? "" : result.strip();
    }

    /**
     * M8：根据批评 issue 列表做**一轮**窄幅重写（不改剧情因果与结局）。
     */
    public String polishNarrativeCriticPass(String content,
                                           int chapterNumber,
                                           WritingPipeline pipeline,
                                           NarrativeProfile profile,
                                           NarrativeCriticReport report,
                                           double temperature,
                                           int maxTokens) {
        if (content == null || content.isBlank() || report == null || !report.hasIssues()) {
            return content;
        }
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        log.info("【叙事批评修订】第{}章 — 处理 {} 条意见, pipeline={}", chapterNumber, report.issues().size(), p.name());
        long startTime = System.currentTimeMillis();

        StringBuilder issueBlock = new StringBuilder();
        for (var i : report.issues()) {
            issueBlock.append("- [").append(i.severity()).append("] ").append(i.detail()).append("\n");
        }

        double t = Math.max(0, Math.min(2, temperature));
        int mt = Math.max(512, Math.min(8192, maxTokens));

        String prompt = String.format("""
                你是小说窄幅修订编辑。根据下列**审读意见**优化行文，使读者读感更顺。
                
                【硬性要求】
                1. **不改剧情**：人物生死、关键事实、道具归属、对立结构、章节结局走向一律不变。
                2. **可改写范围**：节奏、详略、对白自然度、删掉重复交代；可把说明性对白改成动作或场面反应。
                3. **禁止**：新增核心设定、新增重要角色、扩写无关大段、写 meta「本章完」。
                4. **排版**：保留分段；不要 markdown 标题或「---」。
                5. **第三人称**与原文一致。
                
                【叙事管线】%s
                
                【审读意见（须逐项照顾；不必逐条回应，体现在正文里即可）】
                %s
                
                【待修订正文｜第%d章】
                %s
                
                请只输出修订后的完整正文，不要解释。
                %s
                """, p.name(), issueBlock.toString().trim(), chapterNumber, content,
                NarrativeCraftPrompts.narrativeM8FrictionRewriteFooter());

        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .temperature(t)
                .maxTokens(mt)
                .build();
        try {
            String result = chatClient.prompt(prompt).options(opts).call().content();
            long elapsed = System.currentTimeMillis() - startTime;
            if (result == null || result.isBlank()) {
                log.warn("【叙事批评修订】返回空，保留原文 - 耗时: {}ms", elapsed);
                return content;
            }
            log.info("【叙事批评修订】✅ 完成 - 耗时: {}ms, 长度 {} -> {}", elapsed, content.length(), result.length());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("【叙事批评修订】❌ 失败 - 耗时: {}ms，保留原文", elapsed, e);
            return content;
        }
    }

    private static String clipForPrompt(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
