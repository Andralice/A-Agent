package com.start.agent.narrative;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** M3：叙事 Lint 聚合结果。 */
public record NarrativeLintReport(List<NarrativeLintIssue> issues) {

    public static NarrativeLintReport empty() {
        return new NarrativeLintReport(List.of());
    }

    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }

    /** 命中任一即允许触发窄幅修正（与 hasIssues 一致，预留日后分级）。 */
    public boolean shouldOfferFix() {
        return hasIssues();
    }

    public String summary() {
        if (!hasIssues()) return "clean";
        return issues.stream().map(i -> i.type() + ":" + truncate(i.detail(), 40)).collect(Collectors.joining("; "));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 按 type+detail 去重保序。 */
    public static NarrativeLintReport dedupe(List<NarrativeLintIssue> raw) {
        if (raw == null || raw.isEmpty()) return empty();
        Set<String> seen = new LinkedHashSet<>();
        List<NarrativeLintIssue> out = raw.stream()
                .filter(i -> seen.add(i.type() + "\0" + i.detail()))
                .toList();
        return new NarrativeLintReport(out);
    }
}
