package com.start.agent.agent;

/**
 * 大纲生成结果：正文（Markdown）与可选的冲突图谱 JSON（两阶段大纲时的第一阶段产物）。
 */
public record OutlineGenerationResult(String markdown, String outlineGraphJson) {

    public static OutlineGenerationResult markdownOnly(String markdown) {
        return new OutlineGenerationResult(markdown, null);
    }

    public boolean hasGraph() {
        return outlineGraphJson != null && !outlineGraphJson.isBlank();
    }
}
