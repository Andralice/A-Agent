package com.start.agent.narrative;

import com.start.agent.model.WritingPipeline;

import java.util.Locale;

/**
 * M6：叙事「物理引擎」路由——两套动力学互不混用语义（note1：日常连续微扰 vs 压力阈值爆发）。
 * 可由 {@link WritingPipeline} 默认 + {@code writingStyleParams.narrativePhysicsMode} 覆盖。
 */
public enum NarrativePhysicsMode {

    /** 连续微扰：温度式漂移、小事件叠加、轻波动（轻小说 / 日常向默认）。 */
    CONTINUOUS_MICRO,

    /** 压力阈值：积压—触发—爆发—冷却更可接受（爽文 / 粗俗 / 年代压抑向默认）。 */
    STRESS_THRESHOLD;

    public static NarrativePhysicsMode fromPipeline(WritingPipeline pipeline) {
        WritingPipeline p = pipeline == null ? WritingPipeline.POWER_FANTASY : pipeline;
        return switch (p) {
            case LIGHT_NOVEL, SLICE_OF_LIFE -> CONTINUOUS_MICRO;
            default -> STRESS_THRESHOLD;
        };
    }

    /**
     * @param raw 用户 JSON 根字段 {@code narrativePhysicsMode}
     * @param fallback 管线默认
     */
    public static NarrativePhysicsMode parseOverride(String raw, NarrativePhysicsMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "continuous", "continuous_micro", "micro", "daily", "slice", "日常", "连续", "微扰" -> CONTINUOUS_MICRO;
            case "stress", "stress_threshold", "threshold", "burst", "pressure", "压力", "阈值", "爆发" -> STRESS_THRESHOLD;
            default -> fallback;
        };
    }
}
