package com.start.agent.model;

import java.util.Locale;

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

    /**
     * 解析 HTTP 路径段 {@code /api/novel-style/{style}/...} 与 {@code POST .../pipeline} 的 body 字段 {@code pipeline}。
     * 大小写不敏感；未列出的任意字符串回落为 {@link #POWER_FANTASY}（不会抛错）。
     */
    public static WritingPipeline fromPath(String style) {
        if (style == null) {
            return POWER_FANTASY;
        }
        String s = style.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "power-fantasy", "power_fantasy", "powerfantasy", "pf", "default",
                    "shuang", "shuangwen", "爽文" -> POWER_FANTASY;
            case "light", "light-novel", "light_novel", "lightnovel", "ln" -> LIGHT_NOVEL;
            case "slice", "slice-of-life", "slice_of_life", "sliceoflife", "daily" -> SLICE_OF_LIFE;
            case "period", "period-drama", "period_drama", "perioddrama", "age", "era", "年代", "年代文" -> PERIOD_DRAMA;
            case "vulgar", "rough", "俗", "粗俗", "粗口" -> VULGAR;
            default -> POWER_FANTASY;
        };
    }
}
