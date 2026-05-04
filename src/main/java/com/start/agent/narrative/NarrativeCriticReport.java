package com.start.agent.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M8：叙事批评 pass 的结构化结果。
 */
public record NarrativeCriticReport(List<NarrativeCriticIssue> issues) {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }

    public boolean hasIssueAtLeast(int minOrdinalInclusive) {
        if (issues == null) {
            return false;
        }
        for (NarrativeCriticIssue i : issues) {
            if (i != null && i.severityOrdinal() >= minOrdinalInclusive) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从模型输出中提取 JSON 并解析；失败返回 null。
     */
    public static NarrativeCriticReport tryParse(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        String json = extractJsonObject(trimmed);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode arr = root.get("issues");
            if (arr == null || !arr.isArray()) {
                return new NarrativeCriticReport(List.of());
            }
            List<NarrativeCriticIssue> out = new ArrayList<>();
            int cap = Math.min(arr.size(), 12);
            for (int i = 0; i < cap; i++) {
                JsonNode n = arr.get(i);
                if (n == null || !n.isObject()) {
                    continue;
                }
                String sev = textOrEmpty(n.get("severity"));
                String det = textOrEmpty(n.get("detail"));
                if (det.isBlank()) {
                    continue;
                }
                out.add(new NarrativeCriticIssue(sev.isBlank() ? "low" : sev, det));
            }
            boolean tooSmooth = root.path("tooSmooth").asBoolean(false)
                    || root.path("too_smooth").asBoolean(false);
            if (tooSmooth && !hasMediumPlusTooSmoothIssue(out)) {
                List<NarrativeCriticIssue> extended = new ArrayList<>(out.size() + 1);
                extended.add(new NarrativeCriticIssue("medium",
                        "模型判定阅读上过顺：进展过于顺滑或巧合感偏强；请在不改剧情前提下补强迟疑、代价余波或过渡体感。"));
                extended.addAll(out);
                while (extended.size() > 12) {
                    extended.remove(extended.size() - 1);
                }
                return new NarrativeCriticReport(extended);
            }
            return new NarrativeCriticReport(out);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String textOrEmpty(JsonNode n) {
        return n == null || n.isNull() ? "" : n.asText("").strip();
    }

    /** medium+ 且文案已点出「过顺/摩擦/巧合」等，视为模型已落实 tooSmooth 指引，避免重复注入。 */
    private static boolean hasMediumPlusTooSmoothIssue(List<NarrativeCriticIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return false;
        }
        for (NarrativeCriticIssue i : issues) {
            if (i == null || i.severityOrdinal() < 1) {
                continue;
            }
            String d = i.detail() == null ? "" : i.detail().toLowerCase(Locale.ROOT);
            if (d.contains("过顺") || d.contains("顺滑") || d.contains("摩擦") || d.contains("巧合")
                    || d.contains("最优") || d.contains("代价") || d.contains("生硬") || d.contains("过渡")) {
                return true;
            }
        }
        return false;
    }

    private static String extractJsonObject(String s) {
        Matcher m = JSON_OBJECT.matcher(s);
        if (m.find()) {
            return m.group();
        }
        return null;
    }
}
