package com.start.agent.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * M7/M8：单次章节生成中收集叙事 Planner、Lint、可选批评闭环的中间产物，落库为 JSON（{@link com.start.agent.model.Chapter#setNarrativeEngineArtifact}）。
 */
public final class NarrativeEngineArtifactSink {

    private static final int MAX_PLANNER_RAW = 4_000;
    private static final int MAX_PLANNER_FORMATTED = 3_000;

    private String plannerStatus = "unknown";
    private String plannerSkipReason;
    private String plannerRawTruncated;
    private String plannerFormattedPreview;
    private String plannerError;

    private String lintStatus = "unknown";
    private String lintSkipReason;
    private int lintHitCount;
    private final List<NarrativeLintIssue> lintIssues = new ArrayList<>();
    private Boolean lintFixApplied;

    /** M8：批评 pass */
    private String criticStatus = "unknown";
    private String criticSkipReason;
    private String criticRawTruncated;
    private String criticError;
    private int criticIssueCount;
    private final List<NarrativeCriticIssue> criticIssues = new ArrayList<>();
    private Boolean criticRewriteAttempted;
    private Boolean criticRewriteApplied;

    private static final int MAX_CRITIC_RAW = 2_500;

    public void recordPlannerSkipped(String reason) {
        this.plannerStatus = "skipped";
        this.plannerSkipReason = reason == null ? "" : reason;
    }

    public void recordPlannerApplied(String rawResponse, String formattedBlock) {
        this.plannerStatus = "applied";
        this.plannerRawTruncated = clip(rawResponse, MAX_PLANNER_RAW);
        this.plannerFormattedPreview = clip(formattedBlock, MAX_PLANNER_FORMATTED);
    }

    public void recordPlannerError(String message) {
        this.plannerStatus = "error";
        this.plannerError = clip(message, 500);
    }

    /**
     * @param lintEnabled   服务端 narrativeLintEnabled
     * @param fixAttempted  是否实际调用了窄幅修正（含 safeStep 调用）
     * @param fixApplied    修正后正文是否与修正前不同
     */
    public void recordLintScan(NarrativeLintReport report, boolean lintEnabled, boolean fixAttempted, boolean fixApplied) {
        this.lintSkipReason = null;
        if (!lintEnabled) {
            this.lintStatus = "disabled";
            this.lintHitCount = 0;
            this.lintIssues.clear();
            this.lintFixApplied = null;
            return;
        }
        if (report == null || !report.hasIssues()) {
            this.lintStatus = "clean";
            this.lintHitCount = 0;
            this.lintIssues.clear();
            this.lintFixApplied = fixAttempted ? fixApplied : false;
            return;
        }
        this.lintStatus = "hits";
        this.lintHitCount = report.issues().size();
        this.lintIssues.clear();
        int cap = Math.min(report.issues().size(), 40);
        for (int i = 0; i < cap; i++) {
            this.lintIssues.add(report.issues().get(i));
        }
        this.lintFixApplied = fixAttempted ? fixApplied : false;
    }

    public void recordLintSkipped(String reason) {
        this.lintStatus = "skipped";
        this.lintHitCount = 0;
        this.lintIssues.clear();
        this.lintFixApplied = null;
        this.lintSkipReason = (reason == null || reason.isBlank()) ? null : reason;
    }

    public void recordCriticSkipped(String reason) {
        this.criticStatus = "skipped";
        this.criticSkipReason = reason == null ? "" : reason;
        this.criticIssueCount = 0;
        this.criticIssues.clear();
        this.criticRawTruncated = null;
        this.criticError = null;
        this.criticRewriteAttempted = null;
        this.criticRewriteApplied = null;
    }

    public void recordCriticError(String message, String rawResponseTruncated) {
        this.criticStatus = "error";
        this.criticError = clip(message, 500);
        this.criticRawTruncated = clip(rawResponseTruncated, MAX_CRITIC_RAW);
        this.criticIssueCount = 0;
        this.criticIssues.clear();
        this.criticRewriteAttempted = false;
        this.criticRewriteApplied = null;
    }

    public void recordCriticClean(String rawResponseTruncated) {
        this.criticStatus = "clean";
        this.criticSkipReason = null;
        this.criticError = null;
        this.criticRawTruncated = clip(rawResponseTruncated, MAX_CRITIC_RAW);
        this.criticIssueCount = 0;
        this.criticIssues.clear();
        this.criticRewriteAttempted = false;
        this.criticRewriteApplied = false;
    }

    public void recordCriticHits(NarrativeCriticReport report, String rawResponseTruncated) {
        this.criticStatus = "hits";
        this.criticSkipReason = null;
        this.criticError = null;
        this.criticRawTruncated = clip(rawResponseTruncated, MAX_CRITIC_RAW);
        this.criticIssues.clear();
        if (report != null && report.issues() != null) {
            int cap = Math.min(report.issues().size(), 15);
            for (int i = 0; i < cap; i++) {
                NarrativeCriticIssue x = report.issues().get(i);
                if (x != null) {
                    this.criticIssues.add(x);
                }
            }
            this.criticIssueCount = report.issues().size();
        } else {
            this.criticIssueCount = 0;
        }
    }

    public void recordCriticRewrite(boolean attempted, boolean contentChanged) {
        this.criticRewriteAttempted = attempted;
        this.criticRewriteApplied = attempted ? contentChanged : null;
    }

    public String toJsonString(ObjectMapper mapper, int chapterNumber) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("version", 1);
            root.put("chapterNumber", chapterNumber);
            root.put("capturedAt", Instant.now().toString());

            ObjectNode planner = root.putObject("planner");
            planner.put("status", plannerStatus);
            if (plannerSkipReason != null && !plannerSkipReason.isBlank()) {
                planner.put("skipReason", plannerSkipReason);
            }
            if (plannerRawTruncated != null && !plannerRawTruncated.isBlank()) {
                planner.put("rawResponseTruncated", plannerRawTruncated);
            }
            if (plannerFormattedPreview != null && !plannerFormattedPreview.isBlank()) {
                planner.put("formattedScriptPreview", plannerFormattedPreview);
            }
            if (plannerError != null && !plannerError.isBlank()) {
                planner.put("error", plannerError);
            }

            ObjectNode lint = root.putObject("lint");
            lint.put("status", lintStatus);
            if (lintSkipReason != null && !lintSkipReason.isBlank()) {
                lint.put("skipReason", lintSkipReason);
            }
            lint.put("hitCount", lintHitCount);
            if (lintFixApplied != null) {
                lint.put("fixApplied", lintFixApplied);
            }
            ArrayNode arr = lint.putArray("issues");
            for (NarrativeLintIssue i : lintIssues) {
                ObjectNode o = arr.addObject();
                o.put("type", i.type() == null ? "" : i.type());
                o.put("detail", clip(i.detail(), 400));
            }

            ObjectNode critic = root.putObject("critic");
            critic.put("status", criticStatus);
            if (criticSkipReason != null && !criticSkipReason.isBlank()) {
                critic.put("skipReason", criticSkipReason);
            }
            if (criticError != null && !criticError.isBlank()) {
                critic.put("error", criticError);
            }
            if (criticRawTruncated != null && !criticRawTruncated.isBlank()) {
                critic.put("rawResponseTruncated", criticRawTruncated);
            }
            critic.put("issueCount", criticIssueCount);
            if (criticRewriteAttempted != null) {
                critic.put("rewriteAttempted", criticRewriteAttempted);
            }
            if (criticRewriteApplied != null) {
                critic.put("rewriteApplied", criticRewriteApplied);
            }
            ArrayNode cArr = critic.putArray("issues");
            for (NarrativeCriticIssue i : criticIssues) {
                ObjectNode o = cArr.addObject();
                o.put("severity", i.severity() == null ? "" : i.severity());
                o.put("detail", clip(i.detail(), 400));
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"version\":1,\"error\":\"serialize_failed\"}";
        }
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
