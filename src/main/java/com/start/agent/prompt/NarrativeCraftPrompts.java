package com.start.agent.prompt;

import com.start.agent.model.WritingPipeline;
import com.start.agent.model.WritingStyleHints;
import com.start.agent.narrative.CognitionArcSnapshot;
import com.start.agent.narrative.ProseCraftSnapshot;
import com.start.agent.narrative.NarrativePhysicsMode;
import com.start.agent.narrative.NarrativeProfile;

/**
 * 长篇生成共用的叙事质量约束（战力逻辑、反派智力、情感递进、对白、套路感）。
 * 供初稿、统稿审查、润色、终稿去 AI 味等环节复用。
 */
public final class NarrativeCraftPrompts {

    private NarrativeCraftPrompts() {
    }

    /** 大纲阶段：压低默认套路堆叠，要求冲突有具体代价与角度。 */
    public static String outlineAntiTropeBlock() {
        return """
                【反套路与创新（必读）】
                - 避免在同一本书里无脑叠满“退婚+废柴逆袭+天降老爷爷/器物+无脑背叛师徒+脸谱必杀反派”等模板拼盘；
                  若使用其中元素，必须改写触发条件、利益结构或结局走向，让读者感到“似曾相识但逻辑不同”。
                - 每个主要对立角色要写清：当下目标、可承受的代价、信息局限；不要用“为推进剧情而降智”替代理性选择。
                - 力量体系写明边界与稀缺资源（寿元、声望、门派规矩、通缉、因果债等），避免后期随意机械降神。
                - **转折分两种**：一要少写修辞式旁白转折（并非/然而/不如说/对立定义句式），尤其对琐事不要硬拗；二要规划**剧情级起伏**——在每个「大阶段」内部每隔若干章须有真实的局势变化、利害反转或意外分支（这是**长线连载里的节拍**，不是要求你把全书主线塞进十几章）。
                """;
    }

    /**
     * 大纲阶段：宏观卷纲 + 里程碑锚点 + 开篇细纲章数 + 全书路线图下限，避免模型按「短篇」把中后期爆点前置。
     *
     * @param detailedPrefixChapters 开篇须逐章（或高密度）铺完的最少章数
     * @param minRoadmapChapters     路线图须覆盖到的最少末章号（含前文）
     */
    public static String outlineLongFormRoadmapBlock(int detailedPrefixChapters, int minRoadmapChapters) {
        int dp = Math.max(25, detailedPrefixChapters);
        int mr = Math.max(dp + 20, minRoadmapChapters);
        int groupedStart = dp + 1;
        return String.format(
                """
                        【长篇连载节奏（硬性｜禁止把全书主线压缩在十几～二十章内完结）】
                        - 按**长篇连载**构思：先写「全书宏观结构」——划分 **至少 4 个大阶段**（可称卷/篇），依次写明各阶段 **主力矛盾、情感基调、篇幅量级感**（可用「约占全书比例」或「约数十章量级」），让读者感到主线会**分期释放**，不是短篇一口气讲完。
                        - **里程碑锚点**：列出 **3～6 个**全书级重大转折（如身份揭晓、阵营翻盘、终极矛盾浮出水面、终局战前置条件齐备等）；每项必须标注 **「建议不早于第 ___ 章」**。除非你刻意写短篇完结，否则 **严禁**把本应中后期才兑现的核心爆点压在开篇前十章内写完。
                        - 「剧情规划」必须 **同时**包含两块（写在「剧情规划」小节内）：
                          (A) **开篇细纲**：从第 1 章起至少 **%1$d** 章——每章用 **一句话**写清「本章推进了什么具体事件或关系变化」（禁止只有形容词或空话）。
                          (B) **后续长篇路线图**：从第 **%2$d** 章起直至至少第 **%3$d** 章——按 **每 8～15 章为一个段落**写「段落目标 + 本段允许兑现的情绪强度 + 禁止提前透支的中后期梗」；整体覆盖必须达到 **≥ %3$d 章**；末段可略粗，但必须说明还剩哪些 **预留伸展空间**（支线、反派层次升级、情感慢热、地图/势力展开等），不得把全书收成两三段就完结。
                        - **前期配额**：前 ~20 章以铺垫、人物站稳、世界观规则落地、小胜负与小悬念为主；大体量的「中期主线危机」「世界观层级跃迁」须在里程碑锚点之后再分期上场。
                        """,
                dp, groupedStart, mr);
    }

    /** 爽文/玄幻向：战力、反派、套路全文（原「叙事硬约束」）。 */
    private static String chapterDraftHardRulesPowerFantasy() {
        return """
                【叙事硬约束（本章必须遵守，优先于节奏模板）】
                
                A. 战力与战斗可信度
                - 禁止仅用“剑身一震 / 灵机一动 / 气势爆发”等空话击败明显强于己方的敌人；若己方弱、敌方强，
                  破局必须让读者看到「环境借势 / 规则或情报差 / 明确代价之一（负伤、脱力、欠人情、赊账式禁术、借势第三方威慑）」里的至少一种，
                  并写出 2–3 步可复述的招式或战术节奏（佯攻、卡位、借地形、破招时机、佯败诱敌），不要一笔带过。
                - 宝物、残魂、上古意志等外力可以扭转局面，但必须当场交代：动用条件、反噬或后续隐患，让读者感到“赚到了但有账要还”。
                
                B. 反派与对手智商
                - 反派台词少当谜语人机；能通过暴力或欺骗立刻达成目标时，不要长篇独白（除非有明显动机：
                  拖延待援、试探身份、忌惮第三方在场、套取情报、门规枷锁、赌注未结算等——须写出一句内在理由）。
                - 禁止写“识破破绽却轻描放行”；若放行，须有当场可信的更高优先级（更重要猎物、误判、卧底需要、留活口套取资源等）。
                
                """ + chapterDraftHardRulesCommonCd()
                + """
                
                E. 套路感
                - 本章至少安排一处对上述常见玄幻爽点套路的微偏移（例如：退婚改成利益谈判、仇敌留一线因账本、破局不靠全靠外挂而靠信息不对称）。
                  偏移不改变大纲走向，但必须让读者感到不是换皮复述。
                """ + chapterDraftHardRulesCommonF();
    }

    /** C/D：情感与对白 —— 各流水线共用。 */
    private static String chapterDraftHardRulesCommonCd() {
        return """
                
                C. 情感与心理（抑制 AI 味）
                - 禁止同一章内从强烈不信任跃迁到“愿赌上性命托付”；若要靠近，只允许写到「小动作犹豫 + 自保本能仍在 + 半步试探」，
                  把彻底托付留给后续章节用事件堆出来。
                - 少用直通标签句式（如“她突然感到莫大的悲伤”“心底涌上一阵说不清的情绪”）。
                  改为：具体感官细节 + 念头打架 + 基于回忆/证据的推断链，让读者自己感到情绪，而不是被作者点名情绪。
                
                D. 对话与生活感
                - 对话承担剧情功能可以，但避免全员散文腔、金句谜语；每个主要角色至少一两句带口癖、打断、错听、或生活碎嘴，
                  让读者感到“像在活人说话”；高冷角色用简洁动作与半截话表态，不写成长段哲学说教。
                """;
    }

    private static String chapterDraftHardRulesCommonF() {
        return """
                
                F. 修辞转折要稀，剧情转折要狠（观感）
                - **修辞转折**：指旁白里的「不是X，是Y」「并非……而是」「不是……而是」「然而/其实不然/不如说/与其说是……不如说……」等**句子层面的反转口吻**。
                  这类句式**少用、拉长间距**——尤其禁止用在**小事、闲笔、带过即可的细节**（走路、端起茶杯、无伤大雅的表情、一句就能说清的态度）上；琐事**平叙就够**，不要为了「像个作家」硬拗一句翻转。
                - **剧情转折**：指**故事里的事变了**——筹划失败、身份/立场曝光、强敌介入、规矩一改前功尽废、结盟破裂、赌注兑现、信息不对称被捅穿等。**跌宕靠事件与后果来写**，让读者感到压迫和悬念，不要用一串修辞转折来代替真正的情节反转。
                - **配额感**：一章里修辞性转折句式尽量零星（包括「不是X，是Y」）；把「让读者心里一揪」的阅读体验，主要来自 **1～2 个扎实的剧情转折点**（可加铺垫与余震），而不是满篇「其实不是你想的那样」式旁白小结。
                """;
    }

    private static String chapterDraftHardRulesLightNovel() {
        return """
                【叙事硬约束（轻小说向｜本章必须遵守）】
                
                A. 冲突与场面可见性（非玄学战力）
                - 禁止只靠内心独白或「气氛变了」完成翻盘；读者要看见具体场面变化：约定、赌赛、公开对决、投票、社交输赢、道具归属之一。
                - 弱胜强或逆袭靠「规则利用 / 情报差 / 默契配合 / 场外制约」等可复述步骤，不写空话必杀。
                
                B. 对手与配角行动性
                - 「反派」可以是竞争对手、看不顺眼的同学、严苛前辈；其行为要有当场动机（面子、名额、暗恋、人情），少谜语演讲。
                - 禁止「看穿却故意放水」除非写明更高优先级（保全某人、合约未到期、欠人情等）。
                
                """ + chapterDraftHardRulesCommonCd()
                + """
                
                E. 模板微偏移
                - 避免教科书式「偶遇撞满怀→误会→强行组队」无信息增量复读；同样骨架下改动机、场合或赌注，让读者感到新鲜一点。
                
                G. 承接上文但不复述（针对模型通病）
                - **禁止**用大半章或整章复述「上一章已写过的对白、争论与情节」；上一章全文仅供参考连贯性，承接时用 **最多 2～4 句** 交代时空与心理状态即可，立刻进入**本章新场面**。
                - **禁止**同一事实在本章内重复讲两遍以上（换角色口吻复述同一段也不行）；信息只说一次，第二次只允许一句带过后果。
                - 本章相对上一章须有**可见增量**：新冲突、新线索、关系进退、赌注或阶段性目标推进、规则后果落地其一；勿停在「把已知设定再开会讲一遍」。
                
                H. 名词与势力扩容（防设定通胀）
                - 本章**新增**可被读者记住的组织名、协议名、反派代号、抽象规则条目，合计建议 **≤2**；非要引入第三个必须绑定当场剧情用处（立刻挨打、立刻签字、立刻付出代价）。
                - **禁止**用「开会式对白」连续抛出多个纯名词不打场面；黑话要挂在动作里解释，且同一词条全章只定义一次。
                """ + chapterDraftHardRulesCommonF();
    }

    private static String chapterDraftHardRulesSliceOfLife() {
        return """
                【叙事硬约束（日常向｜本章必须遵守）】
                
                A. 生活逻辑与代价
                - 压力与破局须落在钱、时间、人情、面子、健康、家庭期待等可理解事物上；禁止突然空降超自然一拳翻盘（除非本书设定明确为异能日常）。
                - 「解决」要有可见后果：多花了一笔、欠一句道歉、关系远一步或近半步，避免嘴上和解却无描写。
                
                B. 对立与摩擦
                - 对立面可以是家人、同事、路人、自卑心；其行为符合世俗动机，不写玄幻式魔王独白。
                - 误会类剧情要有清晰触发点与化解路径，避免全员不长嘴硬拖。
                
                """ + chapterDraftHardRulesCommonCd()
                + """
                
                E. 日常套路微偏移
                - 本章至少一处对常见日常桥段做小改写（例如冷战由小事引爆但收场不靠天降圣人），不改变大纲走向。
                """ + chapterDraftHardRulesCommonF();
    }

    private static String chapterDraftHardRulesPeriodDrama() {
        return """
                【叙事硬约束（年代文｜本章必须遵守）】
                
                A. 时代规则下的选择
                - 人物进退须受物资、身份、单位、人情网络制约；破局靠可走通的门路（调换、证明、斡旋、隐忍蓄势），禁止穿越赢家式随口改变时代规则。
                - 至少一处写出具体时代物象或流程（票证、定量、班组、邻里规矩等）如何卡住或松开人物。
                
                B. 对立与人情
                - 对立面动机落在生计、名声、成份误解、名额等；少写玄学恐吓式反派。
                - 放行、忍让须符合人情与利弊，不写无脑成全。
                
                """ + chapterDraftHardRulesCommonCd()
                + """
                
                E. 刻板桥段微偏移
                - 若沿用年代剧常见桥段，改写触发条件或代价，让读者感到「像但不是复印件」。
                """ + chapterDraftHardRulesCommonF();
    }

    private static String chapterDraftHardRulesVulgar() {
        return """
                【叙事硬约束（粗俗风｜本章必须遵守）】
                
                A. 冲突与后果
                - 吵、怼、动手要有下一步：围观、报警风险、欠人情、挂彩、丢活儿；禁止骂完无事发生。
                - 禁止「一句狠话敌人全体怂」式空话定局；靠具体筹码、靠山、或狼狈脱身。
                
                B. 对手实在
                - 市井对立要有世俗动机（钱、地盘、面子）；可以坏，但不要全员呆陪衬。
                - 少谜语；能怼能抢时别空转演讲。
                
                """ + chapterDraftHardRulesCommonCd()
                + """
                
                E. 江湖桥段微偏移
                - 同样是茬架或碰瓷，换场合、换目击者反应或后续尾巴，避免复读同一爽点空壳。
                """ + chapterDraftHardRulesCommonF();
    }

    /** 单章初稿硬规则：按流水线拆分（爽文保留战力细则，其余弱化玄幻Combat）。 */
    public static String chapterDraftHardRules(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> chapterDraftHardRulesPowerFantasy();
            case LIGHT_NOVEL -> chapterDraftHardRulesLightNovel();
            case SLICE_OF_LIFE -> chapterDraftHardRulesSliceOfLife();
            case PERIOD_DRAMA -> chapterDraftHardRulesPeriodDrama();
            case VULGAR -> chapterDraftHardRulesVulgar();
        };
    }

    /** 大纲：追读动机与钩子，避免只有事件表。 */
    public static String outlineReaderAppealBlock() {
        return """
                【可读性与追读（大纲阶段）】
                - 除主线矛盾外，写出 **2～3 个读者愿意追下去的理由**：人物缺陷与执念、长期悬念、阶段性报应/救赎预期、独特规则下的博弈空间等（不必是打脸）。
                - 章节规划避免「只有剧情清单」：在每个大阶段内每隔几章标出 **情绪或悬念峰值**（揪心、好笑、惊悚、温情其一），让读者预期下一章「会发生什么」（长篇语境下这是阶段内节拍，不是把全书只规划十几章）。
                - 配角与对立面要有 **可被记住的一点怪癖或说话习惯雏形**，降低全员同声翻译腔的风险。
                """;
    }

    /** 单章可读性与情绪节奏：按流水线微调侧重点。 */
    public static String chapterReaderEngagementBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> chapterReaderEngagementPowerFantasy();
            case LIGHT_NOVEL -> chapterReaderEngagementLightNovel();
            case SLICE_OF_LIFE -> chapterReaderEngagementSliceOfLife();
            case PERIOD_DRAMA -> chapterReaderEngagementPeriodDrama();
            case VULGAR -> chapterReaderEngagementVulgar();
        };
    }

    private static String chapterReaderEngagementPowerFantasy() {
        return """
                【可读性与情绪（本章必读：有趣、代入；≠ 堆砌感伤形容词）】
                - **禁止恒温叙事**：避免全章同一语速、像在写说明书；至少在对峙或对话段落写出 **节奏起伏**（急—缓—再急，或冷一下再炸）。
                - **情绪要有落点**：少用「她很难过/他很愤怒」等标签；改用 **可看见的场面**：手的动作、呼吸、停顿、话到嘴边又咽下、眼神躲开、物件被捏紧之类。**允许强烈反应，但必须挂在具体事件上**。
                - **让读者有反应**：本章至少 **2～3 处**清晰的阅读刺激点（意外一句、处境错位带来的幽默、小额翻盘、小人嘴脸可恨、温情一秒、危机逼近冒冷汗等），类型可轮换，避免从头到尾平平交代设定。
                - **对话要有摩擦**：打断、抢话、冷笑、装听不懂、话里有话；禁止三个人轮流发表散文式演讲。
                - **附着视角**：关键段落贴近某一角色的 **即时感知**（先注意到什么、误判了什么、身体先于脑子动了），不要只用远景罗列代替体验。
                - **趣味来源**：幽默与张力来自 **处境与性格错位**，不靠硬堆网络梗或硬拗金句。
                """;
    }

    private static String chapterReaderEngagementLightNovel() {
        return """
                【可读性与情绪（轻小说｜本章必读）】
                - **对白带头**：关键推进尽量落在对话与即时反应；长篇旁白解释设定不得超过一两小段。
                - **节奏轻快**：允许缓急交替，但避免一章压在沉重虐恋或血腥细节上（除非大纲要求）。
                - **刺激点**：至少 **2～3 处**让读者嘴角动一下或心里一紧（吐槽、误会、小翻盘、尴尬社交），不必用大场面堆砌。
                - **视角附着**：写清谁先听见什么、谁先会错意，减少远景说明书。
                - **语调合一**：吐槽、二次元吐槽役与企业黑话、学术口吻不要无序混搭到同一角色连贯独白里；分给不同人或改成短句打断。
                """;
    }

    private static String chapterReaderEngagementSliceOfLife() {
        return """
                【可读性与情绪（日常向｜本章必读）】
                - **微波澜**：不强求狗血炸裂；用小事写出进退两难、好笑一秒、暖心一秒。
                - **标签情绪禁止**：难过/烦躁要用动作与对话挤出来，少用形容词点名。
                - **刺激点**：2～3 处即可——误会解除一半、抠门心疼钱、熟人一句话戳心等。
                - **对话**：生活碎嘴、打断、听岔；禁止全员散文诗朗诵。
                """;
    }

    private static String chapterReaderEngagementPeriodDrama() {
        return """
                【可读性与情绪（年代文｜本章必读）】
                - **克制而有后劲**：激动写在停顿、手势、沉默里，少用咆哮排比；高潮可以是「一句话把人说愣」。
                - **烟火气刺激点**：粮油烟火气、邻里目光、单位传言——2～3 处让读者感到时代的压迫或松动。
                - **附着视角**：写人物当场算清的账（面子、粮票、人情），少用旁空感叹时代。
                - **禁用俏皮互联网梗**抖机灵压轴（除非人物设定就是极少数会说俏皮话的）。
                """;
    }

    private static String chapterReaderEngagementVulgar() {
        return """
                【可读性与情绪（粗俗风｜本章必读）】
                - **嘴上带劲**：对线要有抢话、反问、冷笑；刺激点可来自难堪与反击。
                - **粗鄙但有场景**：至少 **2～3 处**让读者感到「真吵真闹」或「一脚踹翻局面」的快意，不靠文雅比喻堆砌。
                - **禁止为狠而狠**：狠话挂在具体利害上，不写全员神经病式狂笑。
                - **附着视角**：身体反应先于体面（疼、喘、抹脸、掉头走）。
                """;
    }

    /**
     * 用户文风微参（可选）：拼入大纲/角色/初稿提示。
     * @return 无有效字段时返回空串。
     */
    public static String styleMicroParamsBlock(WritingStyleHints hints) {
        if (hints == null || !hints.hasAny()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【用户文风微调（优先尊重，但与合规及大纲事实冲突时以合规与事实为准）】\n");
        String si = normEnum(hints.getStyleIntensity());
        if (si != null) {
            sb.append(switch (si) {
                case "mild" -> "- **叙事张扬度**：克制内敛，少外放咆哮与夸张排比，转折靠事与细节。\n";
                case "bold" -> "- **叙事张扬度**：允许更外放的情绪与对峙张力，但仍须因果可信、禁止全员降智。\n";
                default -> "- **叙事张扬度**：平衡——该狠的地方落地，该收的地方不写空话。\n";
            });
        }
        String dr = normEnum(hints.getDialogueRatioHint());
        if (dr != null) {
            sb.append(switch (dr) {
                case "low" -> "- **对白占比**：偏低：叙事与动作略多，对白求精不求量。\n";
                case "high" -> "- **对白占比**：偏高：尽量用对话推进，旁白只补缺。\n";
                default -> "- **对白占比**：中等：对白与叙述交替，避免单方长篇演讲。\n";
            });
        }
        String hm = normEnum(hints.getHumorLevel());
        if (hm != null) {
            sb.append(switch (hm) {
                case "low" -> "- **幽默**：少打趣；忌讳场合不写段子感。\n";
                case "high" -> "- **幽默**：允许更多打趣与错位，但不堆过期网络梗、不牺牲人设。\n";
                default -> "- **幽默**：适度：来自处境与性格，少量即可。\n";
            });
        }
        String ps = normEnum(hints.getPeriodStrictness());
        if (ps != null) {
            sb.append(switch (ps) {
                case "loose" -> "- **年代细节**：略宽松：核心物象勿硬错误，细枝末节不为考据拖节奏。\n";
                case "strict" -> "- **年代细节**：严格：称谓、物资、流程勿穿帮；慎用现代思维台词。\n";
                default -> "- **年代细节**：正常：关键处准确，勿出现明显时代错位。\n";
            });
        }
        return sb.toString().trim();
    }

    private static String normEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase();
    }

    /** 一致性审查阶段：把抽象问题转成可改的修订目标。 */
    public static String consistencyReviewNarrativeQualityBlock() {
        return """
                
                7. **叙事质量专审（战力/反派/情感/对白/套路）**：
                   - **战力**：弱胜强若没有可见代价或可复述战术链条，改写为更可说服的破局；删除空洞“震动/灵光”式终结技。
                   - **反派**：检查是否“能抢却独白”“识破却放行”；若为剧情服务而无动机，补足动机或改写行为。
                   - **情感**：检查信任/亲昵是否递进过快；过快则插入犹豫、后怕、反悔念头或肢体语言，不写标签式悲伤。
                   - **对白**：替换谜语宣讲与全员散文腔；补一两处生活噪点或人物口癖。
                   - **套路**：若段落与教科书爽点完全一致且无信息增量，做小幅度差异化改写（不改变大纲事实）。
                   - **转折（修辞≠剧情）**：删掉或改平那些在**琐事**上强行使用的「并非/然而/不如说/对立定义句式」；在**真正有分量的剧情拐点**保留或补强**事件与因果**，让读者感到局势变了，而非作者替角色上课。若修辞转折堆砌到密不透风，删减到稀疏，把好句子留给重头戏。
                   - **趣味与张弛**：若读起来像设定说明或流水账，在不改大纲事实前提下，增强 **1～2 处**对话摩擦、感官落点或小意外；勿把全书磨成同一平板语调。
                """;
    }

    /** 终稿阶段：查漏补缺，不改变事实。 */
    public static String deAiNarrativeFocusBlock() {
        return """
                
                【叙事去皱（不改变事实，只修补写法）】
                - 将标签情绪句改为：细节 + 推断/念头拉扯；删减空洞谜语独白与重复的“高深”句式。
                - **勿过度抹平**：删模板腔与空洞修辞时，保留 **尖锐对白、幽默错位、紧张停顿、生理细节** 等有辨识度的笔触；不要把一章磨成均匀寡淡的“安全正文”。
                - 修辞类转折句式（尤其「不是X，是Y」「并非X，而是Y」「与其说X，不如说Y」）若**密、或与小事绑在一起**：删简为平叙，留出呼吸感；浓墨重彩留给真有剧情起落之处（用事实与动作写起落，少用旁白兜底讲道理）。
                - 战斗若以空话收尾，在不新增设定前提下，补最短必要的战术解释或代价落点。
                - 高冷角色的长段说教改写为少说多做或冷幽默式短对白。
                """;
    }

    // ---- 爽文（POWER_FANTASY）通用：全站复用，不依赖单书题材 ----

    /** 大纲：爽点节奏、反派行动性、阶段兑现。 */
    public static String outlineShuangwenUniversalBlock() {
        return """
                【爽文大纲通用（必读）】
                - 主线要“单核醒目”：长期目标 + 当前阶段目标 + 主要对立面（或规则/制度）要一眼能记住；支线为升级服务，避免多条线同等松散。
                - **进展感**：篇章要有看得见的推进（线索、修为/筹码、局势变化、关系变动其一），但不要求**高频当众打脸**；爽点可来自**智斗脱身、规则内反制、资源落袋、名望微调、结盟借势、险中求全**等，章与章之间允许舒缓与铺垫。
                - **主角体验**：主角可以吃亏、受压，但避免**长时间、反复的公开羞辱与人格践踏**（不为虐而虐）；挫败要有代偿——体面、底线、即时小翻盘、或清晰的「这笔账记下了」式反击预期。
                - 反派与对手：动机落在**利益/规则/信息差/仇怨**，智力在线；少靠谜语独白灌设定，行动要具体（设局、封锁、交易、借力），但不必写成无脑恶人样板。
                - 结构习惯：压抑有度 → 破局可见 → 当场有一点兑现（收获/局面松动/反噬/伏笔回收）→ 轻钩子；避免「压得太狠却迟迟不兑现」拖读者。
                - 同一套路不要无信息增量复读；**不必为打脸而打脸**，换手段、换场合、换赌注。
                """;
    }

    /** 单章初稿：事件链、禁止碎档排版、压 AI 补丁句。 */
    public static String chapterDraftShuangwenUniversalBlock() {
        return """
                【爽文章节通用（本章必读）】
                - 本章必须让读者看到**一件事的起落闭环**：至少包含「具体阻碍/对立 → 可见的应对过程 → 明确结果」。结果可以是**小胜、全身而退、拿到一条关键情报/筹码、场面扳平、或尊严未被碾碎地退场**；**不强制**写成「围观打脸」才算兑现。
                - **委屈上限**：避免本章内对主角**连绵的公开羞辱、人格贬低、虐身虐心只为拖节奏**；若写受压，请同时给**脑子在线的应对、底线、或当场扳回一两分**，让读者感到「难，但不憋屈到想弃书」。
                - 叙述频道保持网文可读：**可快慢切换，但不要整章做戏剧腔/文艺腔/脱口秀腔的剧烈跳台**；狠话与吐槽可以，但要贴人物与场景。
                - **禁止**在正文里使用 Markdown 分隔线「---」、井号标题行「# ...」、以及「（本章完）」等标记；分段只用空行。
                - 「不是X，是Y」「并非X，而是Y」等对立定义句：全章**最多 1 次**，且必须压在**真正重头戏**；其它全部改直叙或改动作。
                - 控制 AI 补丁句总量：如「沉默了很久」「某种更复杂的东西」「很难形容的笑」等，全章合计**不超过 3 次**；需要时换成**旁人的一句嘴、物证、价码、时间压力**。
                - 结尾钩子尽量用**事件**（人来、信到、阵亮、兽吼、追兵至）来勾；少用口号式人生总结。
                """;
    }

    /** 角色 JSON：为爽文提供可打的脸与可追的利。 */
    public static String characterShuangwenUniversalBlock() {
        return """
                【爽文角色档案（补充）】
                - 主角：除 want/fear 外，写清**短期阶段目标**与当前最棘手的**阻碍来源**（人、规矩、资源缺口皆可）；「把人比下去」只是可选爽点，不必人人对标打脸。
                - 主要对立面：动机具体（资源/权位/功法门路/血仇/把柄），**允许有自洽逻辑**；给一条**可预期的行动习惯**（设局、交易、借力），避免纯脸谱蠢恶。
                - 配角可略标签化，但要有**功能**（送情报、当见证、当对照、当资源入口），避免只负责夸主角。
                """;
    }

    /** 一致性审查：在修文时保护爽点信息密度。 */
    public static String consistencyReviewShuangwenUniversalBlock() {
        return """
                
                8. **爽文可读性专审（不改大纲事实，只增可读与兑现感）**：
                   - **进展是否落地**：若只有“气势/情绪”而缺少可见变化，补最短必要的**局面变化**（得失、情报、立场、筹码、体面是否保住）；**不强制**写成围观打脸。
                   - **憋屈是否过量**：若主角连续遭受公开羞辱、人格践踏且无任何应对与代偿，酌情调低羞辱强度，或补一句**当场机灵/底线/小翻盘**，避免无脑受虐感。
                   - **信息密度**：关键对峙若被心理独白与排比冲散，优先改回**对话+动作+物证/价码**。
                   - **删碎档排版**：去掉多余的「---」、markdown 标题与「（本章完）」类痕迹；用空行分段。
                   - **反派效率**：能动手/能设局处不要空转演讲；把“解释设定”压到一两句能推动下一步的话。
                   - **去重复意象**：剑鸣/震动/剑格眼纹/温热感等传感器式描写不要同章反复刷；保留最有力的一两次即可。
                """;
    }

    /** 终稿去 AI 味：避免把爽文高潮收成散文。 */
    public static String deAiShuangwenPreserveBlock() {
        return """
                
                【爽文终稿特别要求】
                - **保护关键情节信息**：不要为“去 AI 味”把破局、交涉、脱身、收获、立场变化等改写成空洞内心总结；可删赘语，但因果与结果要还在。
                - **保留情绪锋芒**：尖锐对白、幽默错位、紧张停顿等与人物黏在一起的笔触不要随意抹平。
                - 删除正文中的「---」、以「#」开头的标题行、以及「（本章完）」等平台违和标记。
                - 仍删除模板化升华句、堆砌的修辞对立定义句；战斗若以空话收尾，仅允最短补丁交代战术或代价（不加新设定）。
                """;
    }

    /** 爽文专用润色附录（接在爽文润色主指令后）。 */
    public static String polishingCraftAddendumShuangwen() {
        return """
                
                【爽文润色补充】
                - 删废话与重复意象，但**保留本章关键进展的事实链条**（谁施压、主角如何应对、局面如何变、得到了什么）；**不必**每场都是当众打脸。
                - 对白去杠杆：少讲解员口吻；狠话短、落地快。
                - 去掉「---」「# 标题」「（本章完）」；战斗慎用“一震定局”，必要时用最短两步因果或代价补丁（不脱纲）。
                - **保趣味**：保留 2～3 处能让读者「有反应」的对白或小节拍；只删套路总结与重复意象，不把全文磨成温水。
                """;
    }

    // ---- 网络热梗（可选开关：全书级；必须克制）----

    /** 大纲：规划可开玩笑的切口，而非章章硬塞。 */
    public static String hotMemeOutlineBlock() {
        return """
                【网络热梗模式（开关已开启｜大纲阶段）】
                - 在「剧情规划」里标注：哪些单元适合**轻度玩梗**（日常、斗嘴、反差），哪些单元**严禁玩梗**（重伤亡、性侵暗示、虐童、映射现实敏感议题、肃穆祭祀等）。
                - 热梗只作为**调味品**：全书层面约定「偶尔出现」，禁止把大纲写成段子合集。
                - 优先安排 **1～2 名角色**具备吐槽/接梗体质（符合人设），其余角色保持原有口吻，避免全员网络化。
                """;
    }

    /** 单章初稿：配额 + 禁区 + 世界观错位约束。 */
    public static String hotMemeChapterDraftBlock() {
        return """
                【网络热梗模式（开关已开启｜本章正文）】
                - **硬配额**：本章正文内网络流行梗、谐音梗、名场面模仿等合计 **不超过 2 处**；宁可少用或本轮不用，严禁刷屏、禁止段段玩梗。
                - **场合禁区**：下列情境 **禁止** 出现任何玩梗：生离死别与丧葬、酷刑羞辱与性侵暗示、少儿受伤害、族群/地域歧视、映射现实政治宗教敏感。
                - **写法**：梗须融入对白或叙述口吻，自然带过；禁止罗列梗名词典、禁止全员同一时间讲同一种梗。
                - **世界观**：古代/修仙背景下慎用纯现代互联网句式；可用「错位感」轻描淡写（读者懂即可），不要破坏沉浸。
                - **时效**：少用极易过期的热搜口号；优先相对耐看、一两句能收住的表达。
                """;
    }

    /** 角色档案：略点一句即可，仍以 JSON 严谨为主。 */
    public static String hotMemeCharacterProfileBlock() {
        return """
                【网络热梗模式（开关已开启｜角色档案）】
                - 可在 **至多一名配角** 的 summary 中体现「偶尔接梗/吐槽」的性格底色，但必须符合世界观与身份。
                - **禁止**在 want/fear/knowledge 里堆梗；主角设定仍以弧光与冲突为先，不要把主角写成段子手 unless 题材本就是喜剧向。
                """;
    }

    /** 一致性审查：多余或不恰当的梗删掉或移位。 */
    public static String consistencyHotMemeReviewBlock() {
        return """
                
                9. **网络热梗专审（若开关开启）**：
                   - 统计正文中的玩梗/网络化吐槽：**超过 2 处则删减至不超过 2 处**，优先保留最贴人物与场合的一处。
                   - 出现在 **禁区场合**（见上文生离死别、酷刑性侵暗示等）的梗：**必须删除或改写为正经叙述**，不改变事实走向。
                   - 若梗破坏严肃人物人设或时代感，改为更克制的口语即可。
                """;
    }

    /** 润色上下文附录（拼入大纲背景后）。 */
    public static String hotMemePolishHintBlock() {
        return """
                
                【网络热梗润色】
                - 开关开启：保留合规热梗 **最多 2 处/章**，删重复与过时堆砌；禁区场合一律去掉梗。
                - 不要为了搞笑追加新梗。
                """;
    }

    /** 终稿去皱：压超标梗。 */
    public static String deAiHotMemeTrimBlock() {
        return """
                
                【网络热梗终稿】
                - 若开关开启：玩梗仍超过 2 处则删至 ≤2；禁区场合不得保留梗；不改剧情事实。
                """;
    }

    // ---- 大纲：多叙事内核（note/note2｜冲突驱动 vs 关系漂移等）----

    /**
     * 大纲阶段：按 {@link WritingPipeline} 切换叙事内核——统一「要先想清楚引擎再铺章节」，避免所有类型共用同一套流水账骨架。
     * 与 {@link #outlinePipelineCraftBlock}、爽文专用块并用；后者补充题材细则，本块强调结构与升级纪律。
     */
    public static String outlineNarrativeKernelBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String chapterDelta = """
                【章节推进纪律（全流水线｜防灌水）】
                - 在「剧情规划」的 **(A) 开篇细纲** 里：每一章的概要句**末尾用括号**标注本章较章初发生的推进类型（至少一种，可组合）：**关系+**（信任/误会/结盟/疏远等）、**目标+**（阶段欲求或任务优先级变化）、**认知+**（获知情报、误判加深、真相露一角等）。
                - **禁止**连续多章只有「发生了某事」却看不出上述任一变化；过渡章须写清「残留张力」或「伏笔沉降」具体是什么（仍标括号）。
                """;
        String kernel = switch (p) {
            case POWER_FANTASY, VULGAR -> """
                    【叙事内核｜冲突升级型（爽文 / 粗俗风适用）】
                    - **引擎**：以「压力—阈值—爆发—余波」为主轨；大纲不是事件列表，而是**冲突如何逐级加码**的设计图。
                    - **setup（写入「冲突结构概要」）**：各用一行写清——主角**核心欲望**（想得到什么）；世界或规则**不允许什么**（约束/代价/对立结构）。
                    - **三类张力**（各至少一条，可极简）：**外部**（敌手、制度、战争、资源规则）；**内部**（性格缺陷、恐惧、自尊与欲求打架）；**关系型**（某关键人物既可助力也可掣肘）。
                    - **升级节奏**：用 **4～8 个短关键词**描述全书张力走向（例如：压制 → 加码 → 破局点 → 反噬 → 再压 → 爆发预留），并与「长篇连载」各**大阶段**对齐，避免前期透支终局。
                    - **拐点**：在「里程碑锚点」之外，标出 **2～4 个**阶段性「局面翻转」——每次都须挂钩**新情报、新同盟/背叛、规则改写、筹码得失**之一，而不是形容词换代。
                    - **人物张力矩阵**：至少列出 **三组**「甲 ↔ 乙：**竞争 / 依赖 / 误解 / 临时同盟 / 生死账**」等定性（可用昵称或身份指代，须与后文主要角色一致）。
                    """;
            case LIGHT_NOVEL -> """
                    【叙事内核｜关系漂移型（轻小说适用）】
                    - **引擎**：以「平衡 → 轻微扰动 → 情绪变化 → 恢复或残留」为主轨；**不是**战斗番式全程加压，避免把日常硬写成无限升级打脸。
                    - **setup**：一行写关系或处境的**起点平衡**；一行写将反复扰动平衡的**核心钩子**（误会、赛程、契约、邻居、社团规则等）。
                    - **微冲突池**：列出 **4～6 个**可轮转的小摩擦源（面子、误会、吃醋、赛程、赌约、家族期望……），供篇章切片使用；**外部/内部/关系**三类仍各至少一笔，但强度以「可搞笑也可认真」为上限 unless 大纲明确要求黑深残。
                    - **升级表述**：全书张力走向用 **微波浪** 描述（例如：稳 → 小起伏 → 回撤 → 再起伏 → 阶段性释然/更大误会预留），禁止照搬爽文「全盘压制→一拳翻盘」语法主宰全纲。
                    - **人物张力矩阵**：至少 **三组**人物对子的关系定性（竞争室友、表面兄妹、损友互助等），写清**谁会引爆误会**。
                    """;
            case SLICE_OF_LIFE -> """
                    【叙事内核｜日常摩擦型（日常向适用）】
                    - **引擎**：状态漂移 + **可理解的生活利害**（钱、时间、人情、面子、健康、家庭期待）；冲突强度贴近真实，少用玄幻式翻盘语法。
                    - **setup**：一行写日常处境的**稳定态**；一行写贯穿全书的**生计/关系核心张力**。
                    - **三类张力**：外部（环境/制度/他人期待）；内部（性格短板、羞于开口）；关系（亲情友情职场）。各至少一条，落实在**具体小事**上。
                    - **张力走向**：4～8 个关键词偏向「闷 → 松动 → 再闷 → 小释怀 → 新压力」，与长篇阶段绑定。
                    - **人物张力矩阵**：至少三组，强调**谁让你为难、谁给你台阶**。
                    """;
            case PERIOD_DRAMA -> """
                    【叙事内核｜制度压抑 + 人际冲突型（年代文适用）】
                    - **引擎**：在 **物质/编制/人情网络** 约束下升级矛盾；对立面优先落在 **单位、邻里、户口粮票、名声** 等可落地压力，少用穿越式认知碾压代替过程。
                    - **setup**：一行写时代下主角**最想改变的处境**；一行写**最难突破的规则或体面**。
                    - **三类张力**：外部（政策、物资、职权）；内部（怕出事、要面子、亲情牵制）；关系（师徒、邻里、组织）。各至少一条，并与 **至少两处具体物象或流程**挂钩（票证、转正、分房等）。
                    - **张力走向**：4～8 词描述「忍 → 试探 → 碰壁 → 转机与代价 → 再收紧」，符合年代节奏。
                    - **人物张力矩阵**：至少三组，标注 **谁握有否决权、谁给温情或把柄**。
                    """;
        };
        return (chapterDelta + "\n" + kernel).trim();
    }

    /**
     * 与 {@link WritingPipeline} 对齐的大纲冲突图谱 engine 令牌（两阶段大纲第一阶段写入 JSON）。
     */
    public static String outlineEngineTokenForPipeline(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY, VULGAR -> "conflict_escalation";
            case LIGHT_NOVEL -> "relationship_drift";
            case SLICE_OF_LIFE -> "daily_friction";
            case PERIOD_DRAMA -> "institutional_pressure";
        };
    }

    /**
     * 两阶段大纲｜第一阶段专用提示：只产出冲突图谱 JSON（note/note2）。
     */
    public static String outlineConflictGraphPhasePrompt(WritingPipeline pipeline, String topic, String settingBlock,
                                                           boolean hotMemeEnabled) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String engine = outlineEngineTokenForPipeline(p);
        String kernelHint = outlineNarrativeKernelBlock(p);
        String hot = hotMemeEnabled ? "\n\n" + hotMemeOutlineBlock() : "";
        // 勿使用 String.formatted：题材/设定中可能含 "%" 字符。
        return """
                你是长篇小说策划编辑，只做一件事：为下列题材输出 **一份冲突图谱 JSON**（不是章节正文、不是 Markdown 大纲全文）。
                
                【题材】
                """
                + topic
                + """
                
                
                """
                + settingBlock
                + """
                
                
                【叙事内核（必须内化进 JSON 语义；勿逐字复述）】
                """
                + kernelHint
                + hot
                + """
                
                
                【引擎令牌】
                JSON 根字段 engine 必须为（精确字符串）：**
                """
                + engine
                + """
                **
                
                【硬性要求】
                - 只输出 **一个 JSON 对象**：禁止 markdown 代码围栏、禁止前言后语。
                - 字符串统一用双引号，合法 JSON。
                
                【Schema（字段齐全）】
                {
                  "schemaVersion": 1,
                  "engine": "
                """
                + engine
                + """
                ",
                  "setup": { "desire": "主角核心欲求", "constraint": "世界或规则的核心约束" },
                  "conflicts": [
                    { "type": "external", "force": "……", "notes": "可选" },
                    { "type": "internal", "force": "……", "notes": "可选" },
                    { "type": "relationship", "force": "……", "notes": "可选" }
                  ],
                  "escalation_keywords": ["至少6个短词或短语，全书张力走向"],
                  "turning_points": [
                    { "label": "拐点简称", "flip": "局面如何变（须挂钩情报/结盟背叛/规则改写/筹码得失之一）" }
                  ],
                  "tension_matrix": [
                    { "from": "角色或阵营指代", "to": "角色或阵营指代", "relation": "竞争|依赖|误解|同盟|压制等" }
                  ],
                  "possible_branch_hints": [
                    { "node": "关键抉择或不确定节点", "outcomes": ["可能走向A","可能走向B"] }
                  ],
                  "cast": [
                    {
                      "name": "全书固定称谓（姓名或唯一绰号，后续阶段禁止改名）",
                      "role": "protagonist | ally | antagonist | supporting（可用中文：主角/盟友/对立/配角）",
                      "want": "核心欲求",
                      "fear": "核心恐惧或软肋",
                      "knowledge": "信息边界（知道什么/刻意不知什么，可简短）",
                      "summary": "一句话戏剧功能定位"
                    }
                  ]
                }
                - conflicts：**至少 3 条**，且 type 必须覆盖 external、internal、relationship 各至少 1 条。
                - tension_matrix：**至少 3 条**；from/to 尽量使用 cast 中已出现的 name。
                - turning_points：**至少 3 条**。
                - escalation_keywords：**至少 6 个**。
                - possible_branch_hints：**至少 2 条**（可扰动节点，便于后续局部重写；权重不必填）。
                - cast：**至少 4 人**（须含 **至少 1 名 protagonist/主角**、**至少 1 名 antagonist/对立或反派**）；每人必填 name、role、want、fear、summary；name 全书唯一不得重复；knowledge 可填空字符串但键须存在。
                """;
    }

    /**
     * 角色档案生成：锚定大纲摘录 + 冲突图谱中的 setup/conflicts/tension_matrix，强制姓名与关系口径一致。
     */
    public static String characterOutlineAnchorBlock(String excerpt, String graphAnchorJson) {
        boolean hasEx = excerpt != null && !excerpt.isBlank();
        boolean hasG = graphAnchorJson != null && !graphAnchorJson.isBlank();
        if (!hasEx && !hasG) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("""
                
                【已定稿｜大纲角色锚定（强制遵守）】
                - 若下列「冲突图谱摘录」含 **cast** 数组：输出 JSON 中每个角色的 name **必须**来自 cast[].name（逐字一致）；可只为 cast 中部分人生成档案条目，但不得改名、不得另造核心姓名。
                - 若无 cast 或 cast 为空：每个角色的 name 须与「大纲摘录」中的**正式姓名/称谓**完全一致（同一人物只能有一个主名写入 name）。
                - 若摘录仅有绰号或职务称谓且无全名，沿用该称谓作为 name，并在 summary 首句用括号注明「大纲称谓」。
                - 禁止凭空改名、替换主要对立角色称谓；禁止引入与摘录主轴无关的全新核心对立角色，除非摘录已有明确伏笔占位。
                - want/fear/knowledge/summary 须与摘录、cast 及图谱语义相容，允许细化禁止颠覆对立结构与姓名口径。
                """);
        if (hasEx) {
            sb.append("""
                    
                    【大纲摘录（角色相关）】
                    """).append(excerpt.trim());
        }
        if (hasG) {
            sb.append("""
                    
                    
                    【冲突图谱摘录（setup / conflicts / tension_matrix）】
                    """).append(graphAnchorJson.trim());
        }
        return sb.toString();
    }

    /** 第二阶段大纲提示：把已定稿图谱锚进 Markdown 生成上下文。 */
    public static String outlineAnchoredGraphBlock(String prettyPrintedJson) {
        if (prettyPrintedJson == null || prettyPrintedJson.isBlank()) {
            return "";
        }
        return """

                【已定稿｜冲突图谱 JSON（下文 Markdown 大纲必须与之对齐）】
                下列 JSON 为上一阶段已定稿。**剧情规划**中的主轴、对立、阶段拐点须能追溯到其中的 setup、conflicts、turning_points、tension_matrix；可用叙述复述，**禁止**引入与之矛盾的核心因果或替换主要对立逻辑。
                escalation_keywords 须在长篇路线图各段中有对应落点；possible_branch_hints 可在「里程碑锚点」中点名预留。
                **cast 数组为本已定稿角色表**：下文「主角设定 / 主要角色 / 对立角色」中出现的**姓名/称谓**必须与 cast[].name **逐字一致**，只可补充外貌与细节，**禁止**改名、禁止另造一组核心角色姓名。
                
                """ + prettyPrintedJson.trim() + "\n";
    }

    // ---- 非爽文流水线：大纲 / 初稿 / 角色 / 审查 / 终稿（POWER_FANTASY 仍走爽文专用块）----

    /** 大纲阶段追加：与 {@link WritingPipeline} 对齐的规划约束（不含爽文块）。 */
    public static String outlinePipelineCraftBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> "";
            case LIGHT_NOVEL -> """
                    【轻小说大纲补充】
                    - 卷/单元目标清晰：「关系变化 + 小谜团 + 阶段性Boss式矛盾」可并存，避免只有世界观说明书。
                    - 配角要有可见的萌点/槽点/执念雏形，便于对白驱动剧情。
                    - 伏笔优先落在「误会、约定、道具、竞赛规则」等可搞笑也可认真的载体上。
                    - **控制设定通胀**：主线锚定 1～2 条核心张力（如一对关系 + 一条谜团）；新势力、新协议、新反派层级勿扁平铺开，按单元递进引入，避免「每章一批新名词」。
                    - 章节概要写法：**事件与情感节拍**优先，少写「开会宣讲设定」式概要；若必须交代规则，标明由哪个场面引爆。
                    """;
            case SLICE_OF_LIFE -> """
                    【日常向大纲补充】
                    - 主线可以是「关系 + 成长 + 局部目标」（学业、社团、家庭、职场其一），冲突以可理解的利害与情绪为主。
                    - 章节规划标出 **生活细节锚点**（天气、三餐、通勤、小钱、人情），避免写成玄幻战力番。
                    - 张力来自「误会、错过、底线、选择」而非单纯打脸；爽感来自小确幸与小翻盘。
                    """;
            case PERIOD_DRAMA -> """
                    【年代文大纲补充】
                    - 标注关键 **物质约束**（粮票、住房、工种、户口、供销）如何制约人物选择。
                    - 对立面尽量落在 **人情网络、单位规矩、资源稀缺**；少用穿越式超前认知秒杀全场。
                    - 重大时间节点与称谓（同志、师傅、单位）要与设定年代一致，避免现代互联网思维硬套。
                    """;
            case VULGAR -> """
                    【粗俗风大纲补充】
                    - 冲突场面规划「嘴炮 + 行动」交替：吵得狠、事也要往前拱，禁止只剩骂街不走剧情。
                    - 笑点来自处境难堪与嘴贱，不靠全员降智；粗口有配额感，勿章章泼洒同一类型的狠话。
                    """;
        };
    }

    /** 初稿硬规则之上追加：按流水线细化本章写法（不含爽文章节块）。 */
    public static String chapterDraftPipelineCraftBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> "";
            case LIGHT_NOVEL -> """
                    【轻小说本章补充】
                    - 对白占比偏高：关键情节尽量用对话与反应推进；内心独白短促、带吐槽感即可。
                    - **禁止**整章沉重说教或世界观长篇演讲；设定拆进拌嘴与情境。
                    - 节奏轻快但有钩子：章末可用 soft cliffhanger（误会未解、约定将至），少用血腥虐杀压轴。
                    - 战力段落若存在，写成「规则内打闹/比划」感亦可，避免血腥堆砌。
                    - **承接**：你已拿到「上一章全文」——**勿复述**，只允许极简衔接；读者刚读过上一章，不许当成失忆复述稿。
                    - **章末**：少用「总结人生意义」式旁白；钩子用**具体场面**（一声响、一条消息、一个人进门），少用「下一章预告」式元叙事。
                    """;
            case SLICE_OF_LIFE -> """
                    【日常向本章补充】
                    - **克制强玄幻战力模板**：除非题材本是异能日常，否则不写连环开挂破局。
                    - 冲突用具体小事落地：一句话伤到人、一笔钱难倒、一次误会冷战；写出代价与台阶。
                    - 情绪曲线平缓但有波纹：允许哭笑，避免歇斯底里长篇；治愈来自行动与细节而非金句总结。
                    - 生活细节至少 2 处与主线因果弱相关但增强可信（路况、饭菜、短信语气等）。
                    """;
            case PERIOD_DRAMA -> """
                    【年代文本章补充】
                    - **时代细节**：至少一处具体物象或流程（票证、定量、班组、邻里串门规矩）推动选择。
                    - 对话口吻贴合年代与身份：少现代网络梗与二次元梗；激动时也克制，多用停顿与动作。
                    - **禁止**为戏剧性强行插入违和的科技、流行语、职场黑话（除非设定允许）。
                    - 压迫感来自制度、人情债、物资与体面，少用玄学一刀破局。
                    """;
            case VULGAR -> """
                    【粗俗风本章补充】
                    - 句子偏短；对白可带刺、带脏字语气词，但 **不出现露骨色情描写、仇恨歧视、恶意骚扰细节**。
                    - 人物嘴上不饶人，但行动逻辑仍要在线；冲突场面「吵—动手或掉头走—后果」要连贯。
                    - **禁止**为耍狠牺牲因果；可以无赖，但不要全员弱智陪衬。
                    """;
        };
    }

    /** 角色档案追加：非爽文流水线（不含爽文角色块）。 */
    public static String characterPipelineCraftBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> "";
            case LIGHT_NOVEL -> """
                    【轻小说角色补充】
                    - 每人标注「说话习惯」一句（口癖、省略、敬语崩坏等）；主角可有反差萌但不要全员卖萌。
                    - want/fear 具体可演：怕丢脸、怕独处、想赢一次某人等，避免空泛「变强」。
                    """;
            case SLICE_OF_LIFE -> """
                    【日常向角色补充】
                    - 人物僵局来自性格与处境：内向、好面子、负担家庭等，写清「为什么会卡住」。
                    - 关系网写清一层即可（家人/室友/同事），避免突然空降多功能工具人。
                    """;
            case PERIOD_DRAMA -> """
                    【年代文角色补充】
                    - 每人写明 **身份标签**（知青、工人、供销社员属等）与当下物质处境。
                    - want/fear 与时代约束绑定：回城、转正、分房、名声、子女前程等。
                    """;
            case VULGAR -> """
                    【粗俗风角色补充】
                    - 标注谁的嘴最损、谁最容易动手、谁其实怂；方便对白分层。
                    - 反派或对立者可卑鄙但动机要世俗（钱、面子、地盘），不写脸谱憨恶。
                    """;
        };
    }

    /** 一致性审查追加：非爽文流水线专审（爽文仍用 {@link #consistencyReviewShuangwenUniversalBlock()}）。 */
    public static String consistencyReviewPipelineBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> "";
            case LIGHT_NOVEL -> """
                    
                    8. **轻小说专审**：
                       - 人设口吻是否前后一致；是否出现超长说教破坏轻快基调。
                       - 「沉重议题」若出现，是否仍保留可读性与分寸，避免一章压垮基调。
                       - **复述**：是否大段重复上一章已交代的情节/对白；若有，删减为一句承接。
                       - **设定通胀**：是否堆砌过多新组织/协议/名词而无当场剧情落点；若无必要则合并或推迟到后续章。
                    """;
            case SLICE_OF_LIFE -> """
                    
                    8. **日常向专审**：
                       - 是否误插入玄幻爽文式连环打脸、战力暴走；若题材非异能向，删改回生活逻辑。
                       - 家务、金钱、人情细节是否自相矛盾（住处、通勤、时间点）。
                    """;
            case PERIOD_DRAMA -> """
                    
                    8. **年代文专审**：
                       - 称谓、物资、制度描写是否穿帮；有无现代流行语、互联网句式污染。
                       - 冲突解决是否过于「穿越赢家爽」，削弱时代压迫的可信度。
                    """;
            case VULGAR -> """
                    
                    8. **粗俗风专审**：
                       - 粗口与攻击性是否过量；有无露骨色情、仇恨言论——有则删改语气保留冲突。
                       - 对话是否只剩骂街而无剧情推进；补上动作或后果落点。
                    """;
        };
    }

    /** 终稿去 AI 味追加：非爽文保留笔触（爽文仍用 {@link #deAiShuangwenPreserveBlock()}）。 */
    public static String deAiPipelinePreserveBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case POWER_FANTASY -> "";
            case LIGHT_NOVEL -> """
                    
                    【轻小说终稿】
                    - 保留轻快对白与节奏感；不要为了去皱把吐槽与俏皮话磨光。
                    - 删除 markdown 痕迹与模板升华句；过长内心独白收紧。
                    """;
            case SLICE_OF_LIFE -> """
                    
                    【日常向终稿】
                    - 保留生活质感细节与温和起伏；禁止改写成鸡血鸡汤或玄幻腔。
                    - 删除空话情绪标签，改用具体小动作或小物件承接。
                    """;
            case PERIOD_DRAMA -> """
                    
                    【年代文终稿】
                    - **禁止**把正文改成段子腔或重度网络梗；克制、烟火气与可信细节优先。
                    - 不要为了「有趣」插入违和的现代比喻；删 AI 腔但保留时代语感。
                    """;
            case VULGAR -> """
                    
                    【粗俗风终稿】
                    - 保留短句与嘴上带刺的辨识度；删模板排比与讲解员口吻即可。
                    - 粗俗限定在语气与市井表达，不新增露骨或仇恨内容。
                    """;
        };
    }

    /** 润色阶段附录：轻小说（接在润色主指令后）。 */
    public static String polishingCraftAddendumLightNovel() {
        return """
                
                【轻小说润色补充】
                - 收紧赘述与说明书式设定句；对白保留锋芒与节奏，不改成公文。
                - 删「不是X，是Y」堆叠；重头戏保留一两处情绪落点即可。
                - 去掉正文中的 markdown 分隔线与标题行。
                - **删复述**：若中段或后段重复开篇已讲过的同一段争论/同一知识点，合并为一句或删掉次要那次。
                - **名词**：同一缩略设定词反复定义多次时，只保留第一次清晰解释。
                """;
    }

    /** 润色阶段附录：日常向。 */
    public static String polishingCraftAddendumSliceOfLife() {
        return """
                
                【日常向润色补充】
                - 删空话与鸡汤总结；保留小事里的体温与停顿。
                - 避免改写成强打脸或战力翻盘句式；冲突落地在关系与选择。
                """;
    }

    /** 润色阶段附录：年代文。 */
    public static String polishingCraftAddendumPeriodDrama() {
        return """
                
                【年代文润色补充】
                - 统一口吻与时代感：删掉网络梗、二次元梗、违和的现代职场黑话。
                - 情绪不外抛成段子；保留克制叙事与生活细节。
                - 不为「去 AI 味」制造荒诞跳台或黑色幽默断层。
                """;
    }

    /** 润色阶段附录：粗俗风。 */
    public static String polishingCraftAddendumVulgar() {
        return """
                
                【粗俗风润色补充】
                - 句子砍短，对线利落；删讲解员式旁白与对称排比。
                - 粗口只精炼不滥堆；绝不写出露骨色情与仇恨煽动。
                - 保留嘴上不饶人与市井真实感，剧情因果不动。
                """;
    }

    /** M4：跨章承接（服务端持久化摘要）；勿复述原文，仅用来校准开篇情绪与场面接续。 */
    public static String narrativeCarryoverBlock(String carryoverText) {
        if (carryoverText == null || carryoverText.isBlank()) {
            return "";
        }
        return """

                【跨章叙事承接｜上一章收束参考（禁止复述摘录原文；开篇须自然接续情绪与场面余波）】
                %s
                """.formatted(carryoverText.trim());
    }

    /** 章节初稿：叙事引擎 1.0 约束块（情绪带宽 + 语言力学 + 禁止项）。 */
    public static String narrativeEngineChapterBlock(NarrativeProfile p) {
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("""
                【叙事引擎 1.0｜本章约束（须遵守；与上文叙事硬约束并用，具体者优先）】
                """);
        sb.append("- **主情绪类型**：").append(p.emotionType()).append("\n");
        sb.append(String.format("""
                - **情绪强度带宽**：模型表达式须在 **%.2f～%.2f** 之间起伏；禁止全程贴顶（从头吼到尾）或全程躺平（无事发生）。
                - **压抑度**：%.2f（越高越「外在克制」：少用直白宣泄；越低允许更强的外显冲突）。
                """, p.intensityMin(), p.intensityMax(), p.suppression()));
        if (p.triggerFact() != null && !p.triggerFact().isBlank()) {
            sb.append("- **触发事实锚点**（优先并入本章因果）：").append(p.triggerFact()).append("\n");
        } else {
            sb.append("- **触发事实锚点**：若上文未给具体事实，则由本章剧情推断，但仍须落在上述强度带宽内。\n");
        }
        if (p.rhythmHint() != null && !p.rhythmHint().isBlank()) {
            sb.append("- **节奏 Intent**：").append(p.rhythmHint()).append("\n");
        }
        if (p.textureHint() != null && !p.textureHint().isBlank()) {
            sb.append("- **语言材质**：").append(p.textureHint()).append("\n");
        }
        if (p.povHint() != null && !p.povHint().isBlank()) {
            sb.append("- **视角 Intent**：").append(p.povHint()).append("\n");
        }
        if (p.affection() != null || p.awkwardness() != null || p.assertiveness() != null) {
            sb.append("- **轻小说微情绪维（0～1，供拿捏互动分寸）**：");
            if (p.affection() != null) {
                sb.append(String.format("好感/亲近 %.2f；", clamp01(p.affection())));
            }
            if (p.awkwardness() != null) {
                sb.append(String.format("别扭/不自在 %.2f；", clamp01(p.awkwardness())));
            }
            if (p.assertiveness() != null) {
                sb.append(String.format("主动推进 %.2f", clamp01(p.assertiveness())));
            }
            sb.append("\n");
        }
        if (p.interactionFocus() != null && !p.interactionFocus().isBlank()) {
            sb.append("- **互动写法**：").append(p.interactionFocus()).append("\n");
        }
        if (!p.forbiddenLines().isEmpty()) {
            sb.append("- **禁止与避讳**：\n");
            for (String line : p.forbiddenLines()) {
                sb.append("  - ").append(line).append("\n");
            }
        }
        if (p.readerInferenceRule()) {
            sb.append("""
                    - **读者推断**：若删掉所有「直接点名情绪的标签句」，读者仍应能从行为与对白判断当下情绪；禁止用标签句偷懒。
                    """);
        }
        sb.append("""
                - **语言力学**：情绪主要靠「句子长短、断裂、停顿、信息与动作」呈现，而不是换更强的形容词。
                """);
        return sb.toString().trim();
    }

    /**
     * M5：情绪波形硬约束 + 管线句法力学 +（适用时）互动显微模板；接在 {@link #narrativeEngineChapterBlock} 之后注入初稿。
     */
    public static String narrativeEngineM5ChapterHardBlock(WritingPipeline pipeline, NarrativeProfile p) {
        if (p == null) {
            return "";
        }
        WritingPipeline pipe = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        StringBuilder sb = new StringBuilder();
        sb.append(narrativeEngineM5WaveformHardBlock());
        sb.append("\n\n").append(narrativeEngineM5PipelineMechanicsBlock(pipe));
        String micro = narrativeEngineM5InteractionMicroBlock(pipe, p);
        if (micro != null && !micro.isBlank()) {
            sb.append("\n\n").append(micro.trim());
        }
        return sb.toString().trim();
    }

    private static String narrativeEngineM5WaveformHardBlock() {
        return """
                【叙事引擎 M5｜情绪波形硬约束】
                - 本章须有可见起伏：至少一处相对「低谷」与一处相对「峰值」，二者均须挂在具体事件、对白或行动中；禁止仅靠形容词轮换伪造起伏。
                - 触发优先：先写清当下发生的事（事实链），再写人物反应；禁止无事件支撑的情绪独白长跑。
                - 区间纪律：强度须在上文给出的带宽内游走；禁止全程贴顶（从头吼到尾）或全程躺平（像压平的心电图）。
                """;
    }

    private static String narrativeEngineM5PipelineMechanicsBlock(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return (switch (p) {
            case LIGHT_NOVEL -> """
                    【M5｜轻小说句法力学】
                    - 禁止连续多段只有心理总结或关系宣判而无对白或可见动作。
                    - 对白要有气口：停顿、打断、欲言又止；全章尽量出现至少一处「话只说一半」或「转移话题」。
                    - 情绪落在小动作、视线、社交距离与语气；少用抽象恋情/敌意宣言代替场面。
                    """;
            case SLICE_OF_LIFE -> """
                    【M5｜日常流句法力学】
                    - 句子以流动、观察为主；少用爽文式短句堆叠整条段落。
                    - 摩擦须落在具体生活细节（钱、时间、人情、面子、物件）；禁止空降超展开消解当下处境。
                    """;
            case PERIOD_DRAMA -> """
                    【M5｜年代文句法力学】
                    - 克制外在爆发：手续、脸面、沉默、眼神先行；陡升仍须有事变支撑。
                    - 物象具体：时代道具与场合先落地，再写人物反应；少用空洞口号式煽情。
                    """;
            case VULGAR -> """
                    【M5｜粗俗流线句法力学】
                    - 短句主导；对线回合清晰；动作优先，少用形容词替场面定性。
                    - 禁止突然插入长段文艺比喻或细腻内心分析，破坏当下口吻。
                    """;
            default -> """
                    【M5｜爽文句法力学】
                    - 铺垫段允许中长句；逼近高潮与高潮段以短句、断裂为主；爆发后可用少量余波长句收束。
                    - 高潮附近至少两处落在动作或对白上，禁止纯旁白形容「很强/很恐怖」代替场面。
                    """;
        }).trim();
    }

    /**
     * 轻小说管线强制；其它管线在用户配置了微情绪维或互动写法时出现。
     */
    private static String narrativeEngineM5InteractionMicroBlock(WritingPipeline pipeline, NarrativeProfile p) {
        WritingPipeline pipe = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        boolean lnPipe = pipe == WritingPipeline.LIGHT_NOVEL;
        boolean lnDims = p.affection() != null || p.awkwardness() != null || p.assertiveness() != null;
        boolean focus = p.interactionFocus() != null && !p.interactionFocus().isBlank();
        if (!lnPipe && !lnDims && !focus) {
            return "";
        }
        return """
                【M5｜互动显微模板（段落颗粒）】
                - 优先结构：可见动作 → 对白 → 微反应（眼神、停顿、手指、距离等小信号至少其一）。
                - 至少一处「犹豫」：话到嘴边收回、假装不在意、先用小事掩饰真实在意。
                - 禁止用一句旁白概括两人关系状态代替上述颗粒（可与上文「互动写法」条目并用）。
                """;
    }

    /**
     * M6：叙事物理引擎分桶（与 M5 并用）；两套动力学语义禁止混用作主导骨架。
     */
    public static String narrativeEngineM6PhysicsBlock(NarrativePhysicsMode mode) {
        if (mode == null) {
            return "";
        }
        return (switch (mode) {
            case CONTINUOUS_MICRO -> """
                    【叙事物理 M6｜连续微扰型（本章主导骨架）】
                    - 情绪像温度缓慢漂移：以小事件、对话气口、视线与距离叠加摩擦；避免「压力槽攒满→大爆炸」贯穿全章（除非本章大纲明确要求强爆发）。
                    - 禁止用爽文式连续短打回合填满本应日常的互动；爆发若出现，须短、有据、有代价。
                    - 不要用「阈值、积压到极限」等措辞主导全章节奏规划（那是另一套物理引擎的语义）。
                    """;
            case STRESS_THRESHOLD -> """
                    【叙事物理 M6｜压力阈值型（本章主导骨架）】
                    - 允许积压—触发—爆发—冷却；爆发须有清晰触发事实与后果，避免无病呻吟式狠话。
                    - 禁止把主线冲突写成无限重复的「小微尴尬波纹」而不推进筹码（与本引擎语义不符）。
                    - 不要用「风铃式全程微波动」替代应有的加压与释放（除非喜剧向章节目标如此）。
                    """;
        }).trim();
    }

    /** 润色阶段短提醒：复读叙事引擎关键禁止项与带宽（不改变剧情）。 */
    public static String narrativeEnginePolishReminder(NarrativeProfile p, WritingPipeline pipeline, NarrativePhysicsMode physicsMode) {
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【叙事引擎 1.0｜润色复读】\n");
        sb.append(String.format("- 保持情绪强度大致在 %.2f～%.2f 带宽内；压抑度倾向 %.2f。\n",
                p.intensityMin(), p.intensityMax(), p.suppression()));
        if (!p.forbiddenLines().isEmpty()) {
            sb.append("- 删除或改写触犯下列写法的内容（若无关则不动剧情）：");
            int n = Math.min(4, p.forbiddenLines().size());
            for (int i = 0; i < n; i++) {
                sb.append(i > 0 ? "；" : "").append(p.forbiddenLines().get(i));
            }
            sb.append("\n");
        }
        if (p.readerInferenceRule()) {
            sb.append("- 去掉空洞情绪标签，用动作与对白保留同等信息量。\n");
        }
        sb.append(narrativeEngineM5PolishSnippet(pipeline, p));
        sb.append(narrativeEngineM6PolishLine(physicsMode));
        return sb.toString().trim();
    }

    private static String narrativeEngineM6PolishLine(NarrativePhysicsMode mode) {
        if (mode == null) {
            return "";
        }
        return switch (mode) {
            case CONTINUOUS_MICRO -> "- 【M6｜润色】保持连续微扰：删大块「阈值爆发」空话，除非与剧情强相关。\n";
            case STRESS_THRESHOLD -> "- 【M6｜润色】保持压力骨架：删细碎俏皮消解核心张力的堆砌（喜剧段落除外）。\n";
        };
    }

    private static String narrativeEngineM5PolishSnippet(WritingPipeline pipeline, NarrativeProfile p) {
        if (p == null) {
            return "";
        }
        WritingPipeline pipe = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String line = switch (pipe) {
            case LIGHT_NOVEL -> "- 【M5｜润色】检查有无动作→对白→微反应链条；删标签句；句长须有快慢对比。\n";
            case SLICE_OF_LIFE -> "- 【M5｜润色】保持流动感为主，避免短打堆满全章；标签句改为具体生活细节。\n";
            case PERIOD_DRAMA -> "- 【M5｜润色】压得住但要有其事；删空话煽情，物象与动作顶替。\n";
            case VULGAR -> "- 【M5｜润色】句子砍短；删文艺比喻污染；狠话落在具体人与事。\n";
            default -> "- 【M5｜润色】高潮附近句长明显短于铺垫；删替场面定性的空洞形容词。\n";
        };
        return line;
    }

    /** 终稿去 AI 味短提醒。 */
    public static String narrativeEngineDeAiReminder(NarrativeProfile p, WritingPipeline pipeline, NarrativePhysicsMode physicsMode) {
        if (p == null) {
            return "";
        }
        WritingPipeline pipe = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String m5 = switch (pipe) {
            case LIGHT_NOVEL -> "- 【M5｜终稿】去掉情绪标签时，优先用停顿、小动作、不完整对白顶替。\n";
            case SLICE_OF_LIFE -> "- 【M5｜终稿】避免突然改成爽文短打腔；保持日常观察口吻。\n";
            case PERIOD_DRAMA -> "- 【M5｜终稿】克制标签煽情；用沉默、手续感与具体物象顶替。\n";
            case VULGAR -> "- 【M5｜终稿】删细腻文艺内心句；保持狠、短、当下。\n";
            default -> "- 【M5｜终稿】标签句改为动作/对白；高潮段保留短句断裂感。\n";
        };
        return """

                【叙事引擎 1.0｜终稿对齐】
                - 不新增剧情；若存在点名情绪的空话，改为具体动作/对白/停顿。
                """ + String.format("- 整体语气仍大致落在情绪带宽 %.2f～%.2f 内。\n", p.intensityMin(), p.intensityMax())
                + m5
                + narrativeEngineM6DeAiLine(physicsMode);
    }

    private static String narrativeEngineM6DeAiLine(NarrativePhysicsMode mode) {
        if (mode == null) {
            return "";
        }
        return switch (mode) {
            case CONTINUOUS_MICRO -> "- 【M6｜终稿】语义引擎=连续微扰：避免改写成全场短打高潮腔。\n";
            case STRESS_THRESHOLD -> "- 【M6｜终稿】语义引擎=压力阈值：避免改写成散文式全程碎碎念。\n";
        };
    }

    private static double clamp01(Double v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(1, v));
    }

    // ---- 叙事阻力层 / 流体润滑 / M8「过顺」检测（写作物理细化）----

    /** Planner 未返回专用阻力字段时，初稿仍注入的默认「摩擦」约束（按管线微调强度）。 */
    public static String narrativeResistanceSoftFallback(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        if (p == WritingPipeline.SLICE_OF_LIFE) {
            return """
                    
                    【本章叙事摩擦｜日常向默认（Planner 未给出专用字段时）】
                    - 本章若以氛围、关系、琐事为主、**无强外部冲突**，禁止为「凑阻力」硬造打脸、悬疑或狗血升级；允许平淡但有 **生活可信的细节与半步顿挫**。
                    - 至少一处（择一即可）：**小事差一点到位**（忘带、耽搁、话题岔开）、**话到嘴边又咽下**、**想做的事被琐事暂时岔开再继续**——用对白或动作点到为止，禁止长篇内心说理。
                    - 避免「全程最优旁白」：允许迟疑半拍、改口、小动作；**不必**写戏剧级二次翻盘或强行代价。
                    - **不改变**大纲规定的本章结局与人物关系事实；静水章宁可留白与余味，也不要事件过山车。
                    """;
        }
        String core = """
                
                【本章叙事阻力｜默认（Planner 未给出专用字段时仍须遵守）】
                - 至少一处：**判断迟疑、信息不全下的决策、或代价权衡**（可用一两句内心或他人打断体现，禁止长篇哲理）。
                - 至少一处：**关键行动未一次性完全成功**——须修正、补救、二次尝试或退一步再进；禁止「一步到位零失误」贯穿重头戏份。
                - 关键兑现须有 **可见代价或时间/面子/资源压力** 之一；禁止仅靠「气势/灵光」无摩擦收尾。
                - **不改变**大纲规定的章节结局事实与关键胜负关系；只丰富「怎么走到结局」的过程摩擦。
                """;
        return switch (p) {
            case LIGHT_NOVEL -> core + "- 轻小说向：摩擦可落在社交丢脸、误会加深、赛程波折；避免血流成河式硬拧。\n";
            case PERIOD_DRAMA -> core + "- 年代向：摩擦落在体面、人情、物资与规矩；少用玄学一刀翻盘。\n";
            case VULGAR -> core + "- 粗俗向：嘴上吃亏、场面难堪可作摩擦；仍要有实事推进。\n";
            default -> core + "- 爽文向：允许压制与翻盘，但翻盘须有 **步骤、代价或情报差**，禁止无病呻吟式狠话后凭空翻盘。\n";
        };
    }

    /** 流体润滑 Pass：仅体验层，不改因果与信息边界。 */
    public static String narrativeFlowSmootherRole(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        String pipe = switch (p) {
            case LIGHT_NOVEL -> "轻小说：对白气口、视线与小动作优先；避免新增设定句。\n";
            case SLICE_OF_LIFE -> "日常：用一两句生活感知做过渡；禁止改成狗血炸裂。\n";
            case PERIOD_DRAMA -> "年代：过渡用语贴合时代，勿插现代梗。\n";
            case VULGAR -> "粗俗：过渡短促，勿突然文艺长比喻。\n";
            default -> "爽文：过渡紧贴当下局势与身体反应，勿灌设定。\n";
        };
        return """
                你是小说「流体润滑」编辑：只做**阅读顺滑与体感连续**，**禁止**改编剧情事实。
                
                【允许】
                - 关键动作前后：补 **一句** 微反应（呼吸、停顿、手指、眼神）衔接语气。
                - 场景切换：补 **一句** 连续感知锚点（声/光/触感/距离），不写新情节。
                - 冲突刚结束：保留 **半拍情绪残留**（身体还未放松、话未说尽），禁止改成立刻鸡汤总结。
                
                【禁止】
                - 新增角色、新道具、新情报层级、改变胜负/生死/归属。
                - 为「润滑」扩写超三章体量的大段写景或回忆。
                - 引入 markdown 标题、「---」、（本章完）。
                
                【管线】
                """ + pipe;
    }

    /** M8 批评：可选顶层字段 tooSmooth；引导模型标记「过于顺滑」。 */
    public static String narrativeM8CriticTooSmoothInstructions() {
        return """
                
                额外顶层字段（可选，必须与 issues 同一 JSON 对象内）：
                "tooSmooth": true/false
                若正文读起来「主角几乎全程最优解、关键信息来得过于刚好、转折巧合感强、场景切换生硬缺乏体感」，请置 tooSmooth 为 true。
                当 tooSmooth 为 true 时，issues 里**至少一条** severity 为 medium 或 high，并具体写出顺滑问题（不超过90字）。
                """;
    }

    /** M8 窄幅重写：若批评涉及过顺/缺摩擦，允许在不改结局前提下做体验层补强。 */
    public static String narrativeM8FrictionRewriteFooter() {
        return """
                
                【过顺 / 摩擦补强（若审读意见涉及「顺滑、缺代价、缺过渡、生硬切换」须落实）】
                在不改变章节结局事实与关键因果前提下：允许加入短暂迟疑、未一次成功的余波、场景切换处的一句感知锚点、对峙后的情绪残留；禁止为此新增核心设定或重要角色。
                """;
    }

    /** 认知弧线：初稿约束（置于叙事引擎 M6 之后）。 */
    public static String cognitionArcChapterBlock(CognitionArcSnapshot arc) {
        if (!arc.enabled()) {
            return "";
        }
        String beatLine = (arc.arcBeatHint() != null && !arc.arcBeatHint().isBlank())
                ? "- 阶段节拍提示：" + arc.arcBeatHint().trim() + "\n"
                : "";
        return String.format("""
                【认知弧线（全书阶段｜人物判断与代价）】
                - 当前叙事阶段：%s（%s）
                - 认知偏置强度（0～1，越高越易被直觉带偏）：%.2f
                - 犹豫类型：%s
                - 决策延迟与表现：%s
                - 错误后果尺度：%s
                %s- 写作约束：人物判断须体现上述偏置与犹豫类型；后果强度须与「错误后果尺度」一致，禁止无故全员降智或无故零代价。
                """,
                arc.phase().labelZh(),
                arc.phase().jsonKey(),
                arc.cognitiveBiasLevel(),
                arc.hesitationType(),
                arc.decisionLatencyHint(),
                arc.errorConsequenceHint(),
                beatLine);
    }

    /** 认知弧线：M2 Planner 对齐说明（附于叙事引擎参数之后）。 */
    public static String cognitionArcPlannerHint(CognitionArcSnapshot arc) {
        if (!arc.enabled()) {
            return "";
        }
        String beat = (arc.arcBeatHint() != null && !arc.arcBeatHint().isBlank())
                ? "- 若与本章节拍相关，可参考阶段提示：" + arc.arcBeatHint().trim() + "\n"
                : "";
        return String.format("""
                【认知弧线对齐（Planner 须遵守）】
                - 全书阶段：%s（%s）；偏置强度约 %.2f；犹豫类型：%s
                - emotionArc 与 beats 中须预留至少一处「非最优判断 / 半步迟疑 / 信息不全下的选择」，与当前阶段偏置一致（勿写成全程冷静最优解）。
                - expectedObstacle / riskPoint / delayMechanism 可与犹豫类型呼应：犹豫体现在场面（停顿、岔开话题、求证、道德两难）而非旁白说教。
                - 后果尺度：若涉及重大误判，closing 或 beats 须指向与当前阶段匹配的代价层级（前期偏轻、后期偏重），禁止轻描淡写一笔带过。
                %s""",
                arc.phase().labelZh(),
                arc.phase().jsonKey(),
                arc.cognitiveBiasLevel(),
                arc.hesitationType(),
                beat);
    }

    /**
     * 文笔四层旋钮（节奏 / 感知权重 / 词粒度 / 信息揭示）；置于认知弧线之后、Planner 之前。
     */
    public static String proseCraftChapterBlock(ProseCraftSnapshot snap) {
        if (snap == null || !snap.enabled()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【文笔旋钮（四章｜优先于堆砌修辞，次于大纲事实与合规）】\n");
        sb.append("说明：下列数值约定在 0～1；未出现的维度沿用模型默认，不必强行凑数。\n\n");

        ProseCraftSnapshot.Rhythm r = snap.rhythm();
        if (r != null && r.present()) {
            sb.append("1）句子节奏（呼吸感）\n");
            lineRhythm(sb, "- 长短句落差（sentenceLengthVariance）", r.sentenceLengthVariance(),
                    "偏低：句长较均匀，可读顺滑；中等：长短交错明显；偏高：长短落差大，戏剧停顿感强。");
            lineRhythm(sb, "- 停顿密度（pauseDensity）", r.pauseDensity(),
                    "偏低：信息流较连贯；中等：逗号分句、留白适中；偏高：停顿与留白多，利于悬疑或咀嚼。");
            lineRhythm(sb, "- 碎片化（fragmentation）", r.fragmentation(),
                    "偏低：整句为主；中等：偶尔碎句；偏高：允许断裂、省略、陡切，勿写成语法错误堆砌。");
            sb.append("\n");
        }

        ProseCraftSnapshot.Perception p = snap.perception();
        if (p != null && p.present()) {
            sb.append("2）感知与镜头权重（Perceptual Focus）\n");
            lineRhythm(sb, "- 感官画面（sensoryWeight）", p.sensoryWeight(),
                    "偏高：光影声触味细节多；偏低：略写感官，避免氛围饱和。");
            lineRhythm(sb, "- 概念/规则解释（conceptualWeight）", p.conceptualWeight(),
                    "偏高：可适当交代设定逻辑；偏低：少讲设定，多用场面暗示。");
            lineRhythm(sb, "- 外部行动推进（externalActionWeight）", p.externalActionWeight(),
                    "偏高：人物在做、在冲突、在推进事件；偏低：行动退居次要（慎用全书长期过低）。");
            lineRhythm(sb, "- 内心活动（internalThoughtWeight）", p.internalThoughtWeight(),
                    "偏高：念头、犹豫、推断多写；偏低：内心收敛，多用动作与对白暗示。");
            sb.append("- 平衡提示：若「感官+内心」明显偏高而「行动」偏低，本章须用具体事件或对话至少推进一处可见的剧情节点，避免纯感受漂移。\n\n");
        }

        ProseCraftSnapshot.Language l = snap.language();
        if (l != null && l.present()) {
            sb.append("3）语言颗粒度（Lexical Granularity）\n");
            lineRhythm(sb, "- 抽象度（abstractionLevel）", l.abstractionLevel(),
                    "偏高：概念与概括多；偏低：落地名词与具体场面多。");
            lineRhythm(sb, "- 用词精度（wordPrecision）", l.wordPrecision(),
                    "偏高：择词克制求准；偏低：白话直给。");
            lineRhythm(sb, "- 形容词/修饰节制（adjectiveControl）", l.adjectiveControl(),
                    "数值越高表示越节制：偏高：少堆砌形容词；偏低：允许更浓的装饰（仍忌陈词滥调）。");
            lineRhythm(sb, "- 术语/知识密度（technicalDensity）", l.technicalDensity(),
                    "偏高：专业词与机制名词可增多，须用上下文锚定；偏低：术语点到为止。");
            sb.append("\n");
        }

        ProseCraftSnapshot.InformationFlow f = snap.informationFlow();
        if (f != null && f.present()) {
            sb.append("4）信息推进方式（Information Delivery）\n");
            if (f.revealType() != null && !f.revealType().isBlank()) {
                sb.append(revealTypeInstruction(f.revealType().trim().toLowerCase()));
            }
            lineRhythm(sb, "- 不确定性维持（uncertaintyMaintenance）", f.uncertaintyMaintenance(),
                    "偏高：保留推断空间，忌作者代替读者宣判；偏低：可适当解释因果。");
            lineRhythm(sb, "- 澄清延迟（clarityDelay）", f.clarityDelay(),
                    "偏高：场面与动作先行，解释延后；偏低：可较早交代关键因果。");
            sb.append("\n");
        }

        sb.append("【综合】文笔 = 信息组织 + 感知选择 + 句法节奏 + 词粒度；各项不必拉满，按本书阶段做有意识的偏置。\n");
        return sb.toString().trim();
    }

    /**
     * 主润色步骤（PolishingAgent.polish）注入：复用 {@link #proseCraftChapterBlock}，并强调修订不得系统性抵消旋钮取向。
     */
    public static String proseCraftPolishBlock(ProseCraftSnapshot snap) {
        String core = proseCraftChapterBlock(snap);
        if (core.isBlank()) {
            return "";
        }
        return core + """

                【润色阶段对齐】
                在合规、审核意见与大纲事实前提下优化词句与局部可读性；须保持上文「文笔旋钮」的整体取向，勿为「顺滑」而把刻意长短错落、留白、分层揭示、术语密度或行动/感知配比一律抹平；若与合规冲突则以合规为准。
                """;
    }

    private static void lineRhythm(StringBuilder sb, String label, Double v, String guide) {
        if (v == null) {
            return;
        }
        sb.append(label).append("≈").append(String.format("%.2f", v)).append("（").append(bandZh(v)).append("）— ").append(guide).append("\n");
    }

    private static String bandZh(double v) {
        if (v < 0.35) {
            return "偏低";
        }
        if (v < 0.65) {
            return "中等";
        }
        return "偏高";
    }

    private static String revealTypeInstruction(String rt) {
        String mode = switch (rt) {
            case "immediate", "direct", "即时" -> """
                    - 揭示策略（revealType=immediate）：允许较早把关键因果说清楚，读者理解成本低；仍避免开篇全盘说明书。
                    """;
            case "layered", "progressive", "分层", "渐进" -> """
                    - 揭示策略（revealType=layered）：分层揭示，本章只兑现一层信息或一处翻转；禁止一次性抖完设定清单。
                    """;
            case "withheld", "held", "隐匿", "压抑" -> """
                    - 揭示策略（revealType=withheld）：维持信息缺口与悬念，禁止过早全盘解说；用场面与线索暗示代替结论先行。
                    """;
            default -> """
                    - 揭示策略（revealType=%s）：按主流长篇习惯做「场面先行、因果后置」，避免开篇整段抽象讲解。
                    """.formatted(rt);
        };
        return mode;
    }
}
