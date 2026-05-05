package com.start.agent.narrative;

/**
 * 认知弧线并入提示词的快照；{@code phase == null} 表示未启用（向后兼容）。
 */
public record CognitionArcSnapshot(
        CognitionArcPhase phase,
        double cognitiveBiasLevel,
        String hesitationType,
        String decisionLatencyHint,
        String errorConsequenceHint,
        String arcBeatHint
) {
    public static CognitionArcSnapshot disabled() {
        return new CognitionArcSnapshot(null, 0.5, "", "", "", null);
    }

    public boolean enabled() {
        return phase != null;
    }

    public static CognitionArcSnapshot defaultsFor(CognitionArcPhase phase) {
        return switch (phase) {
            case EARLY -> new CognitionArcSnapshot(
                    phase,
                    0.8,
                    "perceptual（感知型）",
                    "前期：反应偏慢，易被表象与第一印象带偏；小事可拖延不决。",
                    "前期：误判与冲动的代价偏低，多为面子、误会或可修补的信息差；禁止写成全员无伤大雅的儿戏，但允许试错空间。",
                    null);
            case MID -> new CognitionArcSnapshot(
                    phase,
                    0.5,
                    "analytical（分析型）",
                    "中期：决策前更易反复权衡信息；犹豫落在分析与求证。",
                    "中期：错误开始牵动关系或机会成本，须写出可见后果。",
                    null);
            case LATE -> new CognitionArcSnapshot(
                    phase,
                    0.2,
                    "ethical（价值/决策型）",
                    "后期：小事可果断，大事在关键抉择处允许「卡顿」与迟疑；避免全程优柔寡断。",
                    "后期：重大误判须对应结构性后果（信任崩塌、伤亡、不可逆损失等），不得轻描一笔带过；代价须与设定一致。",
                    null);
        };
    }
}
