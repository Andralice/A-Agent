package com.start.agent.narrative;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 叙事引擎 1.0：章节级「约束中的自由」——情绪带宽、触发锚点、节奏/材质/视角与禁止项。
 * 可由 {@link WritingPipeline} 默认 + {@code writingStyleParams.narrative} JSON 覆盖合并。
 */
public record NarrativeProfile(
        String emotionType,
        double intensityMin,
        double intensityMax,
        double suppression,
        String triggerFact,
        List<String> forbiddenLines,
        String rhythmHint,
        String textureHint,
        String povHint,
        Double affection,
        Double awkwardness,
        Double assertiveness,
        String interactionFocus,
        boolean readerInferenceRule
) {
    public NarrativeProfile {
        Objects.requireNonNull(emotionType, "emotionType");
        intensityMin = clamp(intensityMin, 0, 1);
        intensityMax = clamp(intensityMax, 0, 1);
        if (intensityMax < intensityMin) {
            double t = intensityMin;
            intensityMin = intensityMax;
            intensityMax = t;
        }
        suppression = clamp(suppression, 0, 1);
        forbiddenLines = forbiddenLines == null ? List.of() : List.copyOf(forbiddenLines);
        triggerFact = triggerFact == null ? "" : triggerFact.trim();
        rhythmHint = rhythmHint == null ? "" : rhythmHint.trim();
        textureHint = textureHint == null ? "" : textureHint.trim();
        povHint = povHint == null ? "" : povHint.trim();
        interactionFocus = interactionFocus == null ? "" : interactionFocus.trim();
    }

    /** 供日志与侧车记录的简短摘要（单行级）。 */
    public String toLogSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("emotion=").append(emotionType);
        sb.append(" band=").append(String.format(Locale.ROOT, "%.2f-%.2f", intensityMin, intensityMax));
        sb.append(" sup=").append(String.format(Locale.ROOT, "%.2f", suppression));
        if (affection != null || awkwardness != null || assertiveness != null) {
            sb.append(" ln=(");
            if (affection != null) sb.append("aff=").append(String.format(Locale.ROOT, "%.2f", affection));
            if (awkwardness != null) {
                if (affection != null) sb.append(",");
                sb.append("awk=").append(String.format(Locale.ROOT, "%.2f", awkwardness));
            }
            if (assertiveness != null) {
                if (affection != null || awkwardness != null) sb.append(",");
                sb.append("ass=").append(String.format(Locale.ROOT, "%.2f", assertiveness));
            }
            sb.append(")");
        }
        if (!forbiddenLines.isEmpty()) {
            sb.append(" forbid=").append(Math.min(forbiddenLines.size(), 9)).append("条");
        }
        return sb.toString();
    }

    /**
     * 在默认 profile 上应用 JSON 对象 {@code narrative} 中的字段（仅覆盖出现的键）。
     */
    public static NarrativeProfile applyOverrides(NarrativeProfile base, JsonNode narrative) {
        if (base == null || narrative == null || !narrative.isObject()) {
            return base;
        }
        String emotionType = textOr(base.emotionType(), narrative, "emotionType", "emotion_type");
        Double intensityMin = doubleOrNull(narrative, "intensityMin", "intensity_min");
        Double intensityMax = doubleOrNull(narrative, "intensityMax", "intensity_max");
        Double suppression = doubleOrNull(narrative, "suppression");
        String triggerFact = textOr(base.triggerFact(), narrative, "triggerFact", "trigger_fact");
        String rhythmHint = textOr(base.rhythmHint(), narrative, "rhythmHint", "rhythm_hint");
        String textureHint = textOr(base.textureHint(), narrative, "textureHint", "texture_hint");
        String povHint = textOr(base.povHint(), narrative, "povHint", "pov_hint");
        Double affection = doubleOrNull(narrative, "affection");
        Double awkwardness = doubleOrNull(narrative, "awkwardness");
        Double assertiveness = doubleOrNull(narrative, "assertiveness");
        String interactionFocus = textOr(base.interactionFocus(), narrative, "interactionFocus", "interaction_focus");
        Boolean readerInference = boolOrNull(narrative, "readerInferenceRule", "reader_inference_rule");

        List<String> forbidden = mergeForbidden(base.forbiddenLines(), narrative.path("forbidden"));
        if (forbidden == null) {
            forbidden = base.forbiddenLines();
        }

        return new NarrativeProfile(
                emotionType != null ? emotionType : base.emotionType(),
                intensityMin != null ? intensityMin : base.intensityMin(),
                intensityMax != null ? intensityMax : base.intensityMax(),
                suppression != null ? suppression : base.suppression(),
                triggerFact != null ? triggerFact : base.triggerFact(),
                forbidden,
                rhythmHint != null ? rhythmHint : base.rhythmHint(),
                textureHint != null ? textureHint : base.textureHint(),
                povHint != null ? povHint : base.povHint(),
                affection != null ? affection : base.affection(),
                awkwardness != null ? awkwardness : base.awkwardness(),
                assertiveness != null ? assertiveness : base.assertiveness(),
                interactionFocus != null ? interactionFocus : base.interactionFocus(),
                readerInference != null ? readerInference : base.readerInferenceRule()
        );
    }

    private static List<String> mergeForbidden(List<String> base, JsonNode forbiddenNode) {
        if (forbiddenNode == null || forbiddenNode.isNull()) {
            return null;
        }
        if (!forbiddenNode.isArray()) {
            return base;
        }
        List<String> out = new ArrayList<>(base);
        for (JsonNode n : forbiddenNode) {
            String s = n.asText("").trim();
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static String textOr(String base, JsonNode root, String... keys) {
        for (String k : keys) {
            if (root.has(k) && !root.get(k).isNull()) {
                String v = root.get(k).asText("").trim();
                return v.isEmpty() ? base : v;
            }
        }
        return base;
    }

    private static Double doubleOrNull(JsonNode root, String... keys) {
        for (String k : keys) {
            if (!root.has(k) || root.get(k).isNull()) continue;
            JsonNode n = root.get(k);
            if (n.isNumber()) return n.doubleValue();
            try {
                return Double.parseDouble(n.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean boolOrNull(JsonNode root, String... keys) {
        for (String k : keys) {
            if (!root.has(k) || root.get(k).isNull()) continue;
            JsonNode n = root.get(k);
            if (n.isBoolean()) return n.booleanValue();
            String t = n.asText("").trim().toLowerCase(Locale.ROOT);
            if ("true".equals(t)) return true;
            if ("false".equals(t)) return false;
        }
        return null;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
