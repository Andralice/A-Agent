package com.start.agent.service;

import com.start.agent.model.Chapter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 根据历史章节拼装「长期剧情记忆」文本块，注入到大模型上下文。 */
@Service
public class StoryMemoryService {
    public String buildStoryMemory(List<Chapter> chapters, List<String> lockedNames) {
        if (chapters == null || chapters.isEmpty()) {
            return "【长期剧情记忆】\n无";
        }
        StringBuilder builder = new StringBuilder("【长期剧情记忆】\n");
        appendGlobalTimeline(builder, chapters);
        appendEntityMentions(builder, chapters, lockedNames);
        return builder.toString().trim();
    }

    private void appendGlobalTimeline(StringBuilder builder, List<Chapter> chapters) {
        builder.append("- 全局里程碑:\n");
        int step = Math.max(1, chapters.size() / 8);
        for (int i = 0; i < chapters.size(); i += step) {
            Chapter chapter = chapters.get(i);
            builder.append("  - ").append(chapter.getTitle()).append(": ")
                    .append(clip(chapter.getContent(), 70)).append("\n");
        }
        Chapter last = chapters.get(chapters.size() - 1);
        builder.append("  - 最近章节 ").append(last.getTitle()).append(": ")
                .append(clip(last.getContent(), 90)).append("\n");
    }

    private void appendEntityMentions(StringBuilder builder, List<Chapter> chapters, List<String> lockedNames) {
        if (lockedNames == null || lockedNames.isEmpty()) return;
        builder.append("- 核心角色近期状态:\n");
        for (String lockedName : lockedNames) {
            if (lockedName == null || lockedName.isBlank()) continue;
            List<String> traces = new ArrayList<>();
            for (int i = chapters.size() - 1; i >= 0 && traces.size() < 2; i--) {
                Chapter chapter = chapters.get(i);
                String content = chapter.getContent();
                if (content != null && content.contains(lockedName)) {
                    traces.add(chapter.getTitle() + " => " + extractMentionContext(content, lockedName));
                }
            }
            if (traces.isEmpty()) {
                builder.append("  - ").append(lockedName).append(": 近期未出现\n");
            } else {
                builder.append("  - ").append(lockedName).append(": ").append(String.join(" | ", traces)).append("\n");
            }
        }
    }

    private String extractMentionContext(String content, String name) {
        int idx = content.indexOf(name);
        if (idx < 0) return "未检索到上下文";
        int start = Math.max(0, idx - 25);
        int end = Math.min(content.length(), idx + name.length() + 35);
        return content.substring(start, end).replace("\n", " ");
    }

    private String clip(String content, int maxLen) {
        if (content == null || content.isBlank()) return "无正文";
        String normalized = content.replace("\n", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
