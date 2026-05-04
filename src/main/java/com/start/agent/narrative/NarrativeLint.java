package com.start.agent.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * M3：章节正文叙事 Lint——禁止短语命中 +（可选）直白情绪标签启发式。
 */
public final class NarrativeLint {

    /** readerInferenceRule 为 true 时额外扫描的粗粒度模式（保守，减少误杀）。 */
    private static final Pattern[] EMOTION_LABEL_PATTERNS = new Pattern[] {
            Pattern.compile("很(?:愤怒|恼火|生气|难过|伤心|悲痛|高兴|兴奋|激动|失望|绝望|震惊|恐惧|害怕)"),
            Pattern.compile("(?:不禁|不由得)(?:感到|有些|心头|一阵)"),
            Pattern.compile("一股(?:前所未有|难以名状|说不清|莫名其妙)(?:的)?(?:情绪|感觉|滋味)")
    };

    private NarrativeLint() {
    }

    public static NarrativeLintReport lint(String content, NarrativeProfile profile) {
        if (content == null || content.isBlank() || profile == null) {
            return NarrativeLintReport.empty();
        }
        List<NarrativeLintIssue> raw = new ArrayList<>();
        String text = content;

        for (String phrase : profile.forbiddenLines()) {
            if (phrase == null || phrase.isBlank()) continue;
            if (text.contains(phrase.trim())) {
                raw.add(new NarrativeLintIssue("forbidden_phrase", phrase.trim()));
            }
        }

        if (profile.readerInferenceRule()) {
            for (Pattern p : EMOTION_LABEL_PATTERNS) {
                var m = p.matcher(text);
                if (m.find()) {
                    raw.add(new NarrativeLintIssue("emotion_label_hint", m.group()));
                }
            }
        }

        return NarrativeLintReport.dedupe(raw);
    }
}
