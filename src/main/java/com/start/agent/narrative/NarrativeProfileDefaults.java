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
                0.20,
                0.55,
                0.48,
                "对话可以呛但不要真生气——「嘴上不饶人心里软」是所有互动的底层规则。参照《冰菓》的日常对话节奏和《古书堂事件手帖》的情绪拿捏：冲突来自日常细节（忘了约定、误会、面子、小博弈），收束时一定有当场可见的缓和动作（推过去一杯喝的、先说一句软话但不好好看着对方眼睛说）。",
                List.of(
                        "禁止用一句旁白定性两人的关系状态（如「她很喜欢他」「他们之间的氛围变了」），必须用互动场面让读者自己感觉",
                        "禁止连续三段以上对话没有任何微反应（眼神/手指/停顿/语气变化至少出现一处）",
                        "禁止让角色在拌嘴中途突然变脸长篇说教或讲大道理"
                ),
                "对白场面短句为主（一人一句，二到三回合次局面变化）。叙述段落可稍微放缓——但放缓不要超过一整段，过了就立刻切回对白或动作。",
                "轻暖质地：对话像刚烤好的面包边——外面脆里面软。视觉和听觉细节优先于抽象心理分析（杯子怎么放、谁先移开视线、谁的声音先变了调）。",
                "贴身视角：永远跟苏茗——她注意到什么、谁的语气哪里不对、谁的微表情被漏掉。不要跳到上帝视角替读者做结论。",
                0.55,
                0.42,
                0.38,
                "互动必须是双向的：不能一个人输出一个人接。每次交流都要有来回——问→答→追问或转移。至少全章两处「差点说出口但收了回来」的瞬间。吐槽要快到让对方来不及反击，但反击来了要真的被噎住。",
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
