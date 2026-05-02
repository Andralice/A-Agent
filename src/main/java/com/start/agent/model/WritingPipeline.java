package com.start.agent.model;

/**
 * 长篇生成的文风/叙事流水线枚举；决定各 Agent 使用的提示词分支（爽文、轻小说、日常、年代文等）。
 */
public enum WritingPipeline {
    POWER_FANTASY,
    LIGHT_NOVEL,
    SLICE_OF_LIFE,
    /** 年代文：带时代细节/社会氛围/烟火气的现实向叙事。 */
    PERIOD_DRAMA,
    /** 粗俗风：更口语、更江湖气，但避免低俗露骨与仇恨表达。 */
    VULGAR;

    public static WritingPipeline fromPath(String style) {
        if (style == null) return POWER_FANTASY;
        return switch (style.trim().toLowerCase()) {
            case "light", "light-novel", "ln" -> LIGHT_NOVEL;
            case "slice", "slice-of-life", "daily" -> SLICE_OF_LIFE;
            case "period", "period-drama", "age", "era", "年代", "年代文" -> PERIOD_DRAMA;
            case "vulgar", "rough", "俗", "粗俗", "粗口" -> VULGAR;
            default -> POWER_FANTASY;
        };
    }
}
