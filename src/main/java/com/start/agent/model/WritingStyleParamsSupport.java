package com.start.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

/**
 * {@code writingStyleParams} 根对象：校验「是否包含任一受支持字段」，供存库规范化与扩展字段共存。
 */
public final class WritingStyleParamsSupport {

    private WritingStyleParamsSupport() {
    }

    /** 与 {@link com.start.agent.narrative.CognitionArcResolver} 启用判定一致。 */
    public static boolean cognitionArcObjectMeaningful(JsonNode cog) {
        if (cog == null || !cog.isObject() || cog.isEmpty()) {
            return false;
        }
        Iterator<String> names = cog.fieldNames();
        while (names.hasNext()) {
            String k = names.next();
            if ("byPhase".equals(k) || "by_phase".equals(k)) {
                JsonNode bp = cog.get(k);
                if (bp != null && bp.isObject() && !bp.isEmpty()) {
                    return true;
                }
                continue;
            }
            JsonNode v = cog.get(k);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isTextual() && v.asText().isBlank()) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 根对象是否包含至少一类受支持配置（文风枚举、叙事、cognition、文笔四层等）。
     */
    public static boolean hasSupportedWritingStyleParams(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        if (writingStyleHintsPresent(root)) {
            return true;
        }
        JsonNode narrative = root.get("narrative");
        if (narrative != null && narrative.isObject() && !narrative.isEmpty()) {
            return true;
        }
        if (nonBlankText(root, "narrativePhysicsMode", "narrative_physics_mode")) {
            return true;
        }
        if (nonBlankText(root, "narrativeArcPhase", "narrative_arc_phase")) {
            return true;
        }
        JsonNode cog = root.get("cognitionArc");
        if (cog == null) {
            cog = root.get("cognition_arc");
        }
        if (cognitionArcObjectMeaningful(cog)) {
            return true;
        }
        return proseCraftSignals(root);
    }

    private static boolean writingStyleHintsPresent(JsonNode root) {
        return nonBlankText(root, "styleIntensity", "style_intensity")
                || nonBlankText(root, "dialogueRatioHint", "dialogue_ratio_hint")
                || nonBlankText(root, "humorLevel", "humor_level")
                || nonBlankText(root, "periodStrictness", "period_strictness");
    }

    private static boolean nonBlankText(JsonNode root, String camel, String snake) {
        for (String k : new String[]{camel, snake}) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull() && n.isTextual() && !n.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** 四层文笔任一对象内出现可读旋钮即视为有效。 */
    public static boolean proseCraftSignals(JsonNode root) {
        return proseSectionHasSignal(root, "rhythm",
                "sentenceLengthVariance", "sentence_length_variance",
                "pauseDensity", "pause_density",
                "fragmentation")
                || proseSectionHasSignal(root, "perception",
                "sensoryWeight", "sensory_weight",
                "conceptualWeight", "conceptual_weight",
                "externalActionWeight", "external_action_weight",
                "internalThoughtWeight", "internal_thought_weight")
                || proseSectionHasSignal(root, "language",
                "abstractionLevel", "abstraction_level",
                "wordPrecision", "word_precision",
                "adjectiveControl", "adjective_control",
                "adjectiveDensity", "adjective_density",
                "technicalDensity", "technical_density")
                || proseInformationFlowSignals(root);
    }

    private static boolean proseInformationFlowSignals(JsonNode root) {
        JsonNode n = root.get("informationFlow");
        if (n == null || !n.isObject()) {
            n = root.get("information_flow");
        }
        if (n == null || !n.isObject() || n.isEmpty()) {
            return false;
        }
        if (nonBlankText(n, "revealType", "reveal_type")) {
            return true;
        }
        return n.has("uncertaintyMaintenance") && n.get("uncertaintyMaintenance").isNumber()
                || n.has("uncertainty_maintenance") && n.get("uncertainty_maintenance").isNumber()
                || n.has("clarityDelay") && n.get("clarityDelay").isNumber()
                || n.has("clarity_delay") && n.get("clarity_delay").isNumber();
    }

    private static boolean proseSectionHasSignal(JsonNode root, String sectionKey, String... fieldKeys) {
        JsonNode sec = root.get(sectionKey);
        if (sec == null || !sec.isObject()) {
            return false;
        }
        for (String fk : fieldKeys) {
            JsonNode v = sec.get(fk);
            if (v != null && v.isNumber()) {
                return true;
            }
        }
        return false;
    }
}
