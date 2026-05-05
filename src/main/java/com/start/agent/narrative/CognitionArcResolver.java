package com.start.agent.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.WritingStyleParamsSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从 {@code writingStyleParams} JSON 解析认知弧线：根级 {@code narrativeArcPhase} + {@code cognitionArc}（含可选 {@code byPhase}）。
 */
@Slf4j
@Component
public class CognitionArcResolver {

    private final ObjectMapper objectMapper;

    public CognitionArcResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CognitionArcSnapshot resolve(String writingStyleParamsJson) {
        if (writingStyleParamsJson == null || writingStyleParamsJson.isBlank()) {
            return CognitionArcSnapshot.disabled();
        }
        try {
            JsonNode root = objectMapper.readTree(writingStyleParamsJson.trim());
            JsonNode cog = root.get("cognitionArc");
            if (cog == null) {
                cog = root.get("cognition_arc");
            }

            String phaseRaw = textOrNull(root, "narrativeArcPhase", "narrative_arc_phase");
            boolean hasPhase = phaseRaw != null && !phaseRaw.isBlank();

            if (!hasPhase && !WritingStyleParamsSupport.cognitionArcObjectMeaningful(cog)) {
                return CognitionArcSnapshot.disabled();
            }

            CognitionArcPhase phase = hasPhase ? CognitionArcPhase.parse(phaseRaw) : CognitionArcPhase.EARLY;
            CognitionArcSnapshot merged = CognitionArcSnapshot.defaultsFor(phase);

            if (cog != null && cog.isObject()) {
                merged = applyKnownFields(merged, cog);
                JsonNode byPhase = cog.path("byPhase");
                if (!byPhase.isObject()) {
                    byPhase = cog.path("by_phase");
                }
                if (byPhase.isObject()) {
                    JsonNode slice = byPhase.path(phase.jsonKey());
                    if (slice.isObject()) {
                        merged = applyKnownFields(merged, slice);
                    }
                }
            }

            return merged;
        } catch (Exception e) {
            log.warn("cognition arc resolve skipped: invalid writingStyleParams JSON: {}", e.getMessage());
            return CognitionArcSnapshot.disabled();
        }
    }

    private static CognitionArcSnapshot applyKnownFields(CognitionArcSnapshot base, JsonNode overlay) {
        if (overlay == null || !overlay.isObject()) {
            return base;
        }
        double bias = readDouble(overlay, base.cognitiveBiasLevel(), "cognitiveBiasLevel", "cognitive_bias_level");
        String hes = readText(overlay, base.hesitationType(), "hesitationType", "hesitation_type");
        String lat = readText(overlay, base.decisionLatencyHint(), "decisionLatencyHint", "decision_latency_hint");
        String err = readText(overlay, base.errorConsequenceHint(), "errorConsequenceHint", "error_consequence_hint");
        String beat = readTextAllowBlankOverlay(base.arcBeatHint(), overlay, "arcBeatHint", "arc_beat_hint");
        return new CognitionArcSnapshot(base.phase(), bias, hes, lat, err, beat);
    }

    private static String textOrNull(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull()) {
                String t = n.asText("");
                if (!t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return null;
    }

    private static double readDouble(JsonNode n, double fallback, String k1, String k2) {
        for (String k : new String[]{k1, k2}) {
            JsonNode v = n.get(k);
            if (v != null && v.isNumber()) {
                double d = v.asDouble();
                return clamp01(d);
            }
        }
        return fallback;
    }

    private static String readText(JsonNode n, String fallback, String k1, String k2) {
        for (String k : new String[]{k1, k2}) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                String t = v.asText("");
                if (!t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return fallback;
    }

    /**
     * arcBeatHint：JSON 显式给出（含空串）则覆盖；缺省则保留 base。
     */
    private static String readTextAllowBlankOverlay(String baseHint, JsonNode n, String k1, String k2) {
        for (String k : new String[]{k1, k2}) {
            if (n.has(k)) {
                JsonNode v = n.get(k);
                if (v == null || v.isNull()) {
                    return null;
                }
                String t = v.asText("");
                return t.isBlank() ? null : t.trim();
            }
        }
        return baseHint;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }
}
