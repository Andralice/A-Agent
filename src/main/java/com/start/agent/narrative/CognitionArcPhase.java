package com.start.agent.narrative;

import java.util.Locale;

/**
 * 全书叙事阶段（认知弧线选桶）；与 {@code writingStyleParams.narrativeArcPhase} 对应。
 */
public enum CognitionArcPhase {
    EARLY,
    MID,
    LATE;

    public String jsonKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 前端/文档展示用 */
    public String labelZh() {
        return switch (this) {
            case EARLY -> "前期";
            case MID -> "中期";
            case LATE -> "后期";
        };
    }

    /**
     * 宽松解析：early/mid/late、前后中期等。
     */
    public static CognitionArcPhase parse(String raw) {
        if (raw == null) {
            return EARLY;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return EARLY;
        }
        if (t.equals("early") || t.equals("front") || t.equals("前期") || t.equals("前")) {
            return EARLY;
        }
        if (t.equals("mid") || t.equals("middle") || t.equals("中期") || t.equals("中")) {
            return MID;
        }
        if (t.equals("late") || t.equals("后期") || t.equals("后") || t.equals("终") || t.equals("终盘")) {
            return LATE;
        }
        return EARLY;
    }
}
