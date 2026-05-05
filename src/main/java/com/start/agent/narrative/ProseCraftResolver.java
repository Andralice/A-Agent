package com.start.agent.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从 {@code writingStyleParams} 根节点解析文笔四层：{@code rhythm}、{@code perception}、{@code language}、{@code informationFlow}。
 */
@Slf4j
@Component
public class ProseCraftResolver {

    private final ObjectMapper objectMapper;

    public ProseCraftResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProseCraftSnapshot resolve(String writingStyleParamsJson) {
        if (writingStyleParamsJson == null || writingStyleParamsJson.isBlank()) {
            return ProseCraftSnapshot.disabled();
        }
        try {
            JsonNode root = objectMapper.readTree(writingStyleParamsJson.trim());
            ProseCraftSnapshot.Rhythm r = parseRhythm(root.get("rhythm"));
            ProseCraftSnapshot.Perception p = parsePerception(root.get("perception"));
            ProseCraftSnapshot.Language l = parseLanguage(root.get("language"));
            JsonNode flowNode = root.get("informationFlow");
            if (flowNode == null || !flowNode.isObject()) {
                flowNode = root.get("information_flow");
            }
            ProseCraftSnapshot.InformationFlow f = parseInformationFlow(flowNode);
            ProseCraftSnapshot snap = new ProseCraftSnapshot(r, p, l, f);
            return snap.enabled() ? snap : ProseCraftSnapshot.disabled();
        } catch (Exception e) {
            log.warn("prose craft resolve skipped: {}", e.getMessage());
            return ProseCraftSnapshot.disabled();
        }
    }

    private static ProseCraftSnapshot.Rhythm parseRhythm(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        Double v = d01(n, "sentenceLengthVariance", "sentence_length_variance");
        Double p = d01(n, "pauseDensity", "pause_density");
        Double f = d01(n, "fragmentation");
        ProseCraftSnapshot.Rhythm r = new ProseCraftSnapshot.Rhythm(v, p, f);
        return r.present() ? r : null;
    }

    private static ProseCraftSnapshot.Perception parsePerception(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        Double s = d01(n, "sensoryWeight", "sensory_weight");
        Double c = d01(n, "conceptualWeight", "conceptual_weight");
        Double e = d01(n, "externalActionWeight", "external_action_weight");
        Double i = d01(n, "internalThoughtWeight", "internal_thought_weight");
        ProseCraftSnapshot.Perception p = new ProseCraftSnapshot.Perception(s, c, e, i);
        return p.present() ? p : null;
    }

    private static ProseCraftSnapshot.Language parseLanguage(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        Double a = d01(n, "abstractionLevel", "abstraction_level");
        Double w = d01(n, "wordPrecision", "word_precision");
        Double adj = d01(n, "adjectiveControl", "adjective_control");
        if (adj == null) {
            adj = d01(n, "adjectiveDensity", "adjective_density");
        }
        Double t = d01(n, "technicalDensity", "technical_density");
        ProseCraftSnapshot.Language l = new ProseCraftSnapshot.Language(a, w, adj, t);
        return l.present() ? l : null;
    }

    private static ProseCraftSnapshot.InformationFlow parseInformationFlow(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        String reveal = textOrNull(n, "revealType", "reveal_type");
        if (reveal != null) {
            reveal = reveal.trim().toLowerCase();
        }
        Double u = d01(n, "uncertaintyMaintenance", "uncertainty_maintenance");
        Double c = d01(n, "clarityDelay", "clarity_delay");
        ProseCraftSnapshot.InformationFlow f = new ProseCraftSnapshot.InformationFlow(reveal, u, c);
        return f.present() ? f : null;
    }

    private static Double d01(JsonNode n, String k1, String k2) {
        for (String k : new String[]{k1, k2}) {
            JsonNode v = n.get(k);
            if (v != null && v.isNumber()) {
                double d = v.asDouble();
                return clamp01(d);
            }
        }
        return null;
    }

    private static Double d01(JsonNode n, String k1) {
        JsonNode v = n.get(k1);
        if (v != null && v.isNumber()) {
            return clamp01(v.asDouble());
        }
        return null;
    }

    private static String textOrNull(JsonNode n, String k1, String k2) {
        for (String k : new String[]{k1, k2}) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && v.isTextual()) {
                String t = v.asText("");
                if (!t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return null;
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
