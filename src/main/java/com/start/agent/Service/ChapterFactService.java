package com.start.agent.service;

import com.start.agent.model.ChapterFact;
import com.start.agent.repository.ChapterFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 章节事实 CRUD 与查询：支撑剧情记忆、快照与新角色统计。 */
@Service
public class ChapterFactService {

    /** 轻小说初稿：上一章尾声节选长度上限（汉字约几百～一千），降低复述全文冲动。 */
    private static final int LIGHT_NOVEL_PREV_TAIL_CHARS = 900;
    private final ChapterFactRepository chapterFactRepository;

    public ChapterFactService(ChapterFactRepository chapterFactRepository) {
        this.chapterFactRepository = chapterFactRepository;
    }

    @Transactional
    public void rebuildFactsForChapter(Long novelId, Integer chapterNumber, String content, List<String> lockedNames) {
        rebuildFactsForChapter(novelId, chapterNumber, content, lockedNames, List.of(), null, List.of());
    }

    @Transactional
    public void rebuildFactsForChapter(Long novelId, Integer chapterNumber, String content, List<String> lockedNames,
                                       List<String> sidecarFacts, String continuityAnchor, List<String> sidecarEntities) {
        chapterFactRepository.deleteByNovelIdAndChapterNumber(novelId, chapterNumber);
        List<ChapterFact> facts = extractFacts(novelId, chapterNumber, content, lockedNames);
        for (String sidecarFact : sidecarFacts) {
            if (sidecarFact == null || sidecarFact.isBlank()) continue;
            ChapterFact fact = new ChapterFact();
            fact.setNovelId(novelId);
            fact.setChapterNumber(chapterNumber);
            fact.setFactType("sidecar_fact");
            fact.setSubjectName("sidecar");
            fact.setFactContent(clip(sidecarFact, 200));
            facts.add(fact);
        }
        if (continuityAnchor != null && !continuityAnchor.isBlank()) {
            ChapterFact anchor = new ChapterFact();
            anchor.setNovelId(novelId);
            anchor.setChapterNumber(chapterNumber);
            anchor.setFactType("continuity_anchor");
            anchor.setSubjectName("anchor");
            anchor.setFactContent(clip(continuityAnchor, 200));
            facts.add(anchor);
        }
        for (String entity : sidecarEntities) {
            if (entity == null || entity.isBlank()) continue;
            ChapterFact e = new ChapterFact();
            e.setNovelId(novelId);
            e.setChapterNumber(chapterNumber);
            e.setFactType("sidecar_entity");
            e.setSubjectName(entity.trim());
            e.setFactContent("present");
            facts.add(e);
        }
        for (ChapterFact fact : facts) {
            chapterFactRepository.save(fact);
        }
    }

    public List<ChapterFact> getFactsByNovel(Long novelId) {
        return chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId);
    }

    /**
     * 轻小说流水线专用：用「结构化事实 + 上一章尾声节选」替代整章正文放进初稿 prompt，减少模型复述上一章。
     * 一致性检测等仍应对完整上一章文本进行。
     */
    public String buildLightNovelPreviousChapterBridge(Long novelId, int previousChapterNumber, String fullPreviousContent) {
        String prev = fullPreviousContent == null ? "" : fullPreviousContent.trim();
        String tail = prev.length() <= LIGHT_NOVEL_PREV_TAIL_CHARS
                ? prev
                : prev.substring(prev.length() - LIGHT_NOVEL_PREV_TAIL_CHARS);

        List<ChapterFact> facts = chapterFactRepository.findByNovelIdAndChapterNumberOrderByCreateTimeAsc(novelId, previousChapterNumber);
        String anchor = facts.stream()
                .filter(f -> "continuity_anchor".equals(f.getFactType()))
                .map(ChapterFact::getFactContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
        List<String> sidecarFacts = facts.stream()
                .filter(f -> "sidecar_fact".equals(f.getFactType()))
                .map(ChapterFact::getFactContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        String hook = facts.stream()
                .filter(f -> "chapter_hook".equals(f.getFactType()))
                .map(ChapterFact::getFactContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");

        StringBuilder sb = new StringBuilder();
        sb.append("【上一章衔接材料｜第").append(previousChapterNumber).append("章】\n");
        sb.append("说明：此为摘要与尾声节选，仅供时空与因果连贯；禁止扩写成复述上一章。\n");
        if (!anchor.isEmpty()) {
            sb.append("- 衔接锚点：").append(anchor).append("\n");
        }
        if (!sidecarFacts.isEmpty()) {
            sb.append("- 侧车事实：\n");
            for (String sf : sidecarFacts) {
                sb.append("  · ").append(sf).append("\n");
            }
        }
        if (!hook.isEmpty() && hook.length() <= 400) {
            sb.append("- 章脉钩子（压缩）：").append(hook).append("\n");
        } else if (!hook.isEmpty()) {
            sb.append("- 章脉钩子（压缩）：").append(clip(hook, 400)).append("\n");
        }
        if (anchor.isEmpty() && sidecarFacts.isEmpty() && hook.isEmpty()) {
            sb.append("- （该章尚无结构化侧车记忆，仅依赖尾声节选衔接）\n");
        }
        int tailLen = tail.isEmpty() ? 0 : tail.length();
        sb.append("- 尾声节选（末 ").append(tailLen).append(" 字，勿逐句复读）：\n");
        sb.append(tail.isEmpty() ? "（空）\n" : tail + "\n");
        return sb.toString().trim();
    }

    public String buildFactMemory(Long novelId, int maxFacts) {
        List<ChapterFact> facts = chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId);
        if (facts.isEmpty()) return "【章节事实记忆】\n无";
        StringBuilder builder = new StringBuilder("【章节事实记忆】\n");
        int from = Math.max(0, facts.size() - Math.max(1, maxFacts));
        for (int i = from; i < facts.size(); i++) {
            ChapterFact fact = facts.get(i);
            builder.append("- 第").append(fact.getChapterNumber()).append("章 ")
                    .append(fact.getFactType()).append(" / ")
                    .append(fact.getSubjectName() == null ? "-" : fact.getSubjectName())
                    .append(": ")
                    .append(fact.getFactContent())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private List<ChapterFact> extractFacts(Long novelId, Integer chapterNumber, String content, List<String> lockedNames) {
        List<ChapterFact> facts = new ArrayList<>();
        if (content == null || content.isBlank()) return facts;

        String[] paragraphs = content.split("\\n+");
        for (String lockedName : lockedNames) {
            if (lockedName == null || lockedName.isBlank()) continue;
            for (String paragraph : paragraphs) {
                String line = paragraph.trim();
                if (line.isEmpty()) continue;
                if (line.contains(lockedName)) {
                    ChapterFact fact = new ChapterFact();
                    fact.setNovelId(novelId);
                    fact.setChapterNumber(chapterNumber);
                    fact.setFactType("character_state");
                    fact.setSubjectName(lockedName);
                    fact.setFactContent(clip(line, 160));
                    facts.add(fact);
                    break;
                }
            }
        }

        ChapterFact chapterHook = new ChapterFact();
        chapterHook.setNovelId(novelId);
        chapterHook.setChapterNumber(chapterNumber);
        chapterHook.setFactType("chapter_hook");
        chapterHook.setSubjectName("chapter");
        chapterHook.setFactContent(clip(content.replace("\n", " "), 200));
        facts.add(chapterHook);
        return facts;
    }

    private String clip(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
