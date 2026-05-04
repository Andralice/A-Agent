package com.start.agent.narrative;

import java.util.Locale;

/**
 * M8：叙事批评单条问题（由模型 JSON 解析而来）。
 */
public record NarrativeCriticIssue(String severity, String detail) {

    public int severityOrdinal() {
        String s = severity == null ? "" : severity.trim().toLowerCase(Locale.ROOT);
        if (s.contains("高") || s.equals("high") || s.equals("h")) {
            return 2;
        }
        if (s.contains("中") || s.equals("medium") || s.equals("med") || s.equals("m")) {
            return 1;
        }
        return 0;
    }
}
