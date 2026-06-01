package com.start.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 5 维度 AI 味检测器：词汇层 / 句式层 / 叙事层 / 情感层 / 对话层。
 */
@Service
public class AiFlavorDetector {
    private static final Logger log = LoggerFactory.getLogger(AiFlavorDetector.class);

    // —— 词汇层 ——
    private static final List<String> AI_ADVERBS = List.of("缓缓", "淡淡", "微微", "轻轻", "默默", "渐渐", "深深", "静静");
    private static final List<String> AI_EXPRESSION_TEMPLATES = List.of(
            "眸中闪过", "瞳孔微缩", "心中一凛", "脸色一变", "眼神一凝", "眉头一皱", "嘴角上扬", "眼底闪过",
            "深吸一口气", "心中一沉", "脸色微变", "心头一跳"
    );
    private static final List<String> AI_VOCABULARY = List.of(
            "不置可否", "毋庸置疑", "不言而喻", "可想而知", "显而易见",
            "殊不知", "他不知道的是", "殊不知的是",
            "这一切", "那一瞬间", "在这一刻", "从那一刻起"
    );

    // —— 句式层 ——
    private static final Pattern SUMMARY_ENDING = Pattern.compile(
            "(他终于明白|她终于懂得|他终于意识到|她终于发现|由此可见|这让他|这一切都|从此以后|从那以后).{0,30}[。.]"
    );
    private static final Pattern FOUR_STEP_CLOSURE = Pattern.compile(
            ".{10,80}[，,].{10,80}[，,].{10,80}[，,].{10,80}[。.]"
    );
    private static final List<String> DRAMATIC_IRONY = List.of(
            "他不知道的是", "她不知道的是", "殊不知", "他哪里知道", "她却不知道",
            "他万万没想到", "让她没想到的是", "令他意外的是"
    );

    // —— 情感层 ——
    private static final Pattern LABELED_EMOTION = Pattern.compile(
            "(他|她|它)(感到|觉得|感觉到|觉得非常|感到非常|十分|非常)(愤怒|悲伤|难过|高兴|兴奋|紧张|害怕|恐惧|焦虑|不安|激动)"
    );
    private static final Pattern INSTANT_EMOTION_SWITCH = Pattern.compile(
            ".{5,30}(愤怒|暴怒|大怒).{5,30}(平静|冷静|淡然|淡淡).{0,20}[。.]"
    );

    // —— 对话层 ——
    private static final Pattern EXPLAIN_AFTER_DIALOG = Pattern.compile(
            "[」』\"'].{0,20}(他这么说是因为|这话的意思是|言下之意是|其实是在说|这句话的含义是)"
    );

    public enum FlavorDimension { VOCABULARY, SENTENCE, NARRATIVE, EMOTION, DIALOGUE }

    public record AiFlavorIssue(FlavorDimension dimension, String severity, String location,
                                 String description, String evidence, String fixHint, boolean blocking) {}

    /**
     * 对正文执行完整的 5 维 AI 味检测。
     */
    public List<AiFlavorIssue> detect(String content) {
        if (content == null || content.isBlank()) {
            return List.of(new AiFlavorIssue(FlavorDimension.NARRATIVE, "critical",
                    "全文", "正文为空", "", "无法检测", true));
        }

        List<AiFlavorIssue> issues = new ArrayList<>();
        issues.addAll(detectVocabulary(content));
        issues.addAll(detectSentencePattern(content));
        issues.addAll(detectNarrativePattern(content));
        issues.addAll(detectEmotionPattern(content));
        issues.addAll(detectDialoguePattern(content));
        return issues;
    }

    // ──── 1. 词汇层 ────
    private List<AiFlavorIssue> detectVocabulary(String content) {
        List<AiFlavorIssue> issues = new ArrayList<>();

        // 万能副词在 500 字窗口内的密度检测
        int adverbHits = 0;
        for (String adv : AI_ADVERBS) {
            int idx = 0;
            while ((idx = content.indexOf(adv, idx)) >= 0) {
                adverbHits++;
                idx += adv.length();
            }
        }
        int totalWindows = Math.max(1, content.length() / 500);
        double density = (double) adverbHits / totalWindows;
        if (density > 3) {
            issues.add(new AiFlavorIssue(FlavorDimension.VOCABULARY, "high",
                    "全文", "高频万能副词", "全文共" + adverbHits + "处（密度" + String.format("%.1f", density) + "/500字窗口）",
                    "删除[缓缓/淡淡/微微]等万能副词，换为具体动作描写", true));
        } else if (density > 1.5) {
            issues.add(new AiFlavorIssue(FlavorDimension.VOCABULARY, "medium",
                    "全文", "万能副词偏多", "全文共" + adverbHits + "处",
                    "适当减少万能副词，增加具体描写", false));
        }

        // 神态模板检测
        int templateHits = 0;
        List<String> hitTemplates = new ArrayList<>();
        for (String tmpl : AI_EXPRESSION_TEMPLATES) {
            if (content.contains(tmpl)) {
                templateHits++;
                if (hitTemplates.size() < 3) hitTemplates.add(tmpl);
            }
        }
        if (templateHits >= 4) {
            issues.add(new AiFlavorIssue(FlavorDimension.VOCABULARY, "high",
                    "全文", "神态模板密集使用",
                    "出现" + templateHits + "个模板，如：" + String.join("、", hitTemplates),
                    "将神态模板替换为具体行为和微动作", true));
        }

        return issues;
    }

    // ──── 2. 句式层 ────
    private List<AiFlavorIssue> detectSentencePattern(String content) {
        List<AiFlavorIssue> issues = new ArrayList<>();

        // 总结句收尾检测
        long summaryEndings = SUMMARY_ENDING.matcher(content).results().count();
        if (summaryEndings >= 3) {
            issues.add(new AiFlavorIssue(FlavorDimension.SENTENCE, "high",
                    "全文", "段末总结句过多", summaryEndings + "处总结性收尾",
                    "删除[他终于明白/由此可见]等总结句，留余味", true));
        }

        // 戏剧性反讽检测
        List<String> foundIrony = new ArrayList<>();
        for (String phrase : DRAMATIC_IRONY) {
            if (content.contains(phrase)) foundIrony.add(phrase);
        }
        if (!foundIrony.isEmpty()) {
            issues.add(new AiFlavorIssue(FlavorDimension.SENTENCE, "medium",
                    "全文", "使用了戏剧性反讽提示",
                    String.join("、", foundIrony),
                    "用场景和细节暗示而非直接告诉读者", false));
        }

        // 同构句检测（简化：检查连续句号分隔的句子是否有相似长度模式）
        String[] sentences = content.split("[。.!！?？]");
        int parallelCount = 0;
        for (int i = 0; i < sentences.length - 2; i++) {
            int l1 = sentences[i].trim().length();
            int l2 = sentences[i + 1].trim().length();
            int l3 = sentences[i + 2].trim().length();
            if (l1 > 8 && l2 > 8 && l3 > 8
                    && Math.abs(l1 - l2) <= 4 && Math.abs(l2 - l3) <= 4) {
                parallelCount++;
            }
        }
        if (parallelCount >= 3) {
            issues.add(new AiFlavorIssue(FlavorDimension.SENTENCE, "medium",
                    "全文", "存在连续同构句", parallelCount + "组连续三句长度相近",
                    "变化句式长度，制造疏密对比", false));
        }

        return issues;
    }

    // ──── 3. 叙事层 ────
    private List<AiFlavorIssue> detectNarrativePattern(String content) {
        List<AiFlavorIssue> issues = new ArrayList<>();

        // "安全着陆"检测：检查最后一段是否以完美解决结尾
        String lastPart = content.length() > 500 ? content.substring(content.length() - 500) : content;
        boolean safeLanding = lastPart.matches(".*"
                + "(一切.*结束|终于.*完成|问题.*解决|危机.*解除|所有.*尘埃落定|万事.*大吉)"
                + ".{0,50}[。.]$");
        if (safeLanding) {
            issues.add(new AiFlavorIssue(FlavorDimension.NARRATIVE, "high",
                    "章末", "章末安全着陆——冲突完美解决，无遗留不安感",
                    "最后一段：" + truncate(lastPart.trim(), 60),
                    "章末留未解决的问题或不安感，不全部收束", true));
        }

        // 展示后解释检测
        int explainAfterShow = 0;
        java.util.regex.Matcher m = EXPLAIN_AFTER_DIALOG.matcher(content);
        while (m.find()) explainAfterShow++;
        if (explainAfterShow > 0) {
            issues.add(new AiFlavorIssue(FlavorDimension.NARRATIVE, "medium",
                    "全文", "对话后紧跟解释性叙述", explainAfterShow + "处",
                    "删掉解释，让动作和对话自己说话", false));
        }

        return issues;
    }

    // ──── 4. 情感层 ────
    private List<AiFlavorIssue> detectEmotionPattern(String content) {
        List<AiFlavorIssue> issues = new ArrayList<>();

        long labeledEmotions = LABELED_EMOTION.matcher(content).results().count();
        if (labeledEmotions >= 3) {
            issues.add(new AiFlavorIssue(FlavorDimension.EMOTION, "high",
                    "全文", "标签化情绪描写", labeledEmotions + "处[他感到X]句式",
                    "用生理反应+微动作替代[他感到X]", true));
        }

        long instantSwitches = INSTANT_EMOTION_SWITCH.matcher(content).results().count();
        if (instantSwitches > 0) {
            issues.add(new AiFlavorIssue(FlavorDimension.EMOTION, "medium",
                    "全文", "情绪即时切换，无过渡", instantSwitches + "处",
                    "增加情绪过渡的微动作或内心独白", false));
        }

        // 统一反应模板检测
        long microShrink = countOccurrences(content, "瞳孔微缩");
        long heartChill = countOccurrences(content, "心中一凛");
        if (microShrink + heartChill >= 3) {
            issues.add(new AiFlavorIssue(FlavorDimension.EMOTION, "medium",
                    "全文", "多个角色使用同一套反应模板",
                    "瞳孔微缩" + microShrink + "次、心中一凛" + heartChill + "次",
                    "为不同角色设计差异化的情绪反应方式", false));
        }

        return issues;
    }

    // ──── 5. 对话层 ────
    private List<AiFlavorIssue> detectDialoguePattern(String content) {
        List<AiFlavorIssue> issues = new ArrayList<>();

        // 对话信息宣讲检测（简化：检测长段对白中包含解释性关键词）
        int infoDump = 0;
        String[] paragraphs = content.split("\n");
        for (String p : paragraphs) {
            if (p.contains("「") || p.contains("」") || p.contains("\"") || p.contains("“") || p.contains("”") || p.contains("：") || p.contains(":")) {
                boolean isExplanation = p.contains("原来") || p.contains("其实") || p.contains("这件事")
                        || p.contains("因为") || p.contains("所以") || p.contains("要知道");
                if (isExplanation && p.length() > 60) infoDump++;
            }
        }
        if (infoDump >= 2) {
            issues.add(new AiFlavorIssue(FlavorDimension.DIALOGUE, "high",
                    "全文", "对话信息宣讲", infoDump + "处疑似用对话解释背景",
                    "将背景信息融入场景和动作，而非角色对话", true));
        }

        return issues;
    }

    /**
     * 生成审查报告 JSON。
     */
    public Map<String, Object> buildReport(String content) {
        List<AiFlavorIssue> issues = detect(content);
        long blockingCount = issues.stream().filter(AiFlavorIssue::blocking).count();
        long highCount = issues.stream().filter(i -> "high".equals(i.severity)).count();

        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> issueList = new ArrayList<>();
        for (AiFlavorIssue issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("severity", issue.severity);
            item.put("category", "ai_flavor");
            item.put("subCategory", issue.dimension.name().toLowerCase());
            item.put("description", issue.description);
            item.put("evidence", issue.evidence);
            item.put("fixHint", issue.fixHint);
            item.put("blocking", issue.blocking);
            issueList.add(item);
        }
        report.put("issues", issueList);
        report.put("summary", issues.size() + "个AI味问题：" + blockingCount + "个阻断，" + highCount + "个高优");
        report.put("passed", blockingCount == 0);
        return report;
    }

    private long countOccurrences(String content, String phrase) {
        int count = 0, idx = 0;
        while ((idx = content.indexOf(phrase, idx)) >= 0) {
            count++;
            idx += phrase.length();
        }
        return count;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
