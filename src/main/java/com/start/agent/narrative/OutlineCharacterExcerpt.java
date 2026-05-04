package com.start.agent.narrative;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从 Markdown 大纲中抽取与角色档案对齐相关的段落（非结构化大纲的务实摘取）。
 */
public final class OutlineCharacterExcerpt {

    private static final List<String> SECTION_KEYS = List.of(
            "世界观设定",
            "主角设定",
            "主要角色",
            "对立角色",
            "冲突结构概要"
    );

    /** 见到下列标题则停止当前块（不含当前块标题行）。 */
    private static final List<String> STOP_KEYS = List.of(
            "世界观设定",
            "主角设定",
            "主要角色",
            "对立角色",
            "冲突结构概要",
            "剧情规划",
            "写作风格要求",
            "写作风格"
    );

    private OutlineCharacterExcerpt() {
    }

    /**
     * 抽取角色相关摘录，总长度不超过 {@code maxChars}（从头部优先保留）。
     */
    public static String extract(String outlineMarkdown, int maxChars) {
        if (outlineMarkdown == null || outlineMarkdown.isBlank() || maxChars <= 0) {
            return "";
        }
        String[] lines = outlineMarkdown.split("\\R");
        List<SectionHit> hits = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            for (String key : SECTION_KEYS) {
                if (isHeadingLine(lines[i], key)) {
                    hits.add(new SectionHit(i, key));
                    break;
                }
            }
        }
        hits.sort(Comparator.comparingInt(h -> h.lineIndex));
        StringBuilder sb = new StringBuilder();
        for (SectionHit hit : hits) {
            String block = grabBlock(lines, hit.lineIndex, hit.key);
            if (!block.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("\n\n---\n\n");
                }
                sb.append(block);
            }
        }
        String out = sb.toString().trim();
        if (out.length() <= maxChars) {
            return out;
        }
        return out.substring(0, maxChars) + "\n\n…（摘录已截断；姓名与关系以截断前内容为准）";
    }

    private static String grabBlock(String[] lines, int headingIdx, String titleKey) {
        StringBuilder sb = new StringBuilder();
        sb.append(lines[headingIdx].trim()).append('\n');
        for (int j = headingIdx + 1; j < lines.length; j++) {
            if (isHeadingLineAny(lines[j], STOP_KEYS) && !lineContainsKeyAsHeading(lines[j], titleKey)) {
                break;
            }
            sb.append(lines[j]).append('\n');
        }
        return sb.toString().trim();
    }

    private static boolean lineContainsKeyAsHeading(String line, String key) {
        return line != null && isHeadingLine(line, key);
    }

    private static boolean isHeadingLineAny(String line, List<String> keys) {
        if (line == null) {
            return false;
        }
        for (String k : keys) {
            if (isHeadingLine(line, k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 避免正文误匹配：要求像标题行，且包含关键字。
     */
    private static boolean isHeadingLine(String line, String key) {
        if (line == null || key == null) {
            return false;
        }
        String t = line.trim();
        if (t.length() > 96 || !t.contains(key)) {
            return false;
        }
        return t.startsWith("#")
                || t.startsWith("-")
                || t.startsWith("*")
                || t.startsWith("【")
                || t.matches("^\\d+[\\.、].*");
    }

    private record SectionHit(int lineIndex, String key) {
    }
}
