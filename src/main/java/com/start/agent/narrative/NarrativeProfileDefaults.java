package com.start.agent.narrative;

import com.start.agent.model.WritingPipeline;

import java.util.List;

/**
 * 按 {@link WritingPipeline} 提供叙事引擎默认参数（可与用户 JSON 合并）。
 */
public final class NarrativeProfileDefaults {

    private NarrativeProfileDefaults() {
    }

    public static NarrativeProfile forPipeline(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case LIGHT_NOVEL -> lightNovel();
            case SLICE_OF_LIFE -> sliceOfLife();
            case PERIOD_DRAMA -> periodDrama();
            case VULGAR -> vulgar();
            default -> powerFantasy();
        };
    }

    private static NarrativeProfile powerFantasy() {
        return new NarrativeProfile(
                "张力释放",
                0.45,
                0.92,
                0.25,
                "以本章实际冲突与赌注为准：先把「发生了什么事」写清，再写反应。",
                List.of(
                        "禁止单独用旁白句点名「他很愤怒/无比震惊/前所未有的恐惧」等代替场面",
                        "禁止用一串修辞转折代替真实剧情反转",
                        "高潮段优先短句、断裂、动作与对白落地，少用总结式升华",
                        "禁止整章情绪贴在同一强度档位（全程亢奋或全程平板）；起伏须挂在具体事件与对白上"
                ),
                "整体节奏：压—压—爆—余波；爆发段句长明显短于铺垫段。",
                "偏爽利、硬朗；感官具体（痛、热、冷、距离），少抽象形容堆叠。",
                "贴近主角即时感官与选择，关键抉择处给清晰念头但不要长篇哲学。",
                null,
                null,
                null,
                "",
                true
        );
    }

    private static NarrativeProfile lightNovel() {
        return new NarrativeProfile(
                "微波动",
                0.18,
                0.58,
                0.55,
                "以日常互动中的具体小事为准：误会、停顿、试探、撤回皆可，避免连续火山式爆发。",
                List.of(
                        "禁止高频「暴怒/崩溃大吼」式强爆发 unless 本章节拍规划明确要求",
                        "禁止用一句话总结两人感情（如「她很喜欢他又不敢说」）代替场面",
                        "禁止整章只有推进没有互动细节：要有可见的动作与对白颗粒",
                        "禁止连续多段纯旁白交代关系状态而不给对白或可见小动作"
                ),
                "日常心跳式：平→小波动→收回→再波动；避免单调节奏。",
                "软、细、轻；多对白气口与停顿，描写落在小动作与视线。",
                "偏近距：表情、语气、社交距离、迟疑更好读。",
                0.55,
                0.42,
                0.38,
                "每段尽量包含：动作 + 对白 + 微反应；至少一处犹豫、停顿、话只说一半或转移话题。",
                true
        );
    }

    private static NarrativeProfile sliceOfLife() {
        return new NarrativeProfile(
                "生活温度",
                0.12,
                0.48,
                0.50,
                "以可理解的生活摩擦为准（钱、时间、人情、面子），情绪像温度缓慢变化。",
                List.of(
                        "禁止突然空降玄幻式一拳翻盘（除非本书设定明确）",
                        "禁止长段内心哲理替代具体生活细节",
                        "少用结论句宣布情绪，多用环境与生活动作折射"
                ),
                "长句与流动感偏多；偶尔插入短句打断即可，不要通篇碎片。",
                "柔软、观察型；多一点「看见了什么」而非「判断了什么」。",
                "中近距；内心可有，但要短、具体、和当下任务绑定。",
                null,
                null,
                null,
                "冲突收场要有可见后果（多花了一点钱、欠一句道歉、关系远近半步）。",
                true
        );
    }

    private static NarrativeProfile periodDrama() {
        return new NarrativeProfile(
                "克制与后劲",
                0.28,
                0.72,
                0.62,
                "以时代规则与人情网络下的具体难处为准；情绪压在手续、脸面、生计里。",
                List.of(
                        "禁止现代网络爽梗的空降消解时代压迫（除非喜剧向设定）",
                        "少用西式玄幻修辞堆情绪",
                        "禁止空洞口号式煽情代替人物选择"
                ),
                "沉稳推进；允许小节制的陡升，但要挂在具体事件上。",
                "质朴、有烟火气；物象具体（票证、单位、邻里）。",
                "中距为主，关键处贴近人物眼底与手头动作。",
                null,
                null,
                null,
                "",
                true
        );
    }

    private static NarrativeProfile vulgar() {
        return new NarrativeProfile(
                "压迫与爆发",
                0.52,
                0.95,
                0.22,
                "以当场冲突与筹码为准：吵、怼、脱身都要有下一步代价。",
                List.of(
                        "禁止一句狠话后全场静默认输的空话定局",
                        "禁止长段细腻心理分析（与本流水线口语短打冲突）",
                        "少用文艺比喻堆砌；狠话要落在具体人与具体事上"
                ),
                "短句为主，停顿多；攒压后爆发，爆发后有狼狈或尾巴。",
                "硬、刺、口语；动作优先，减少抽象词。",
                "贴近现场，减少全景抒情。",
                null,
                null,
                null,
                "",
                true
        );
    }
}
