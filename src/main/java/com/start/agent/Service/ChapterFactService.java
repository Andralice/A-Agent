package com.start.agent.service;

import com.start.agent.model.ChapterFact;
import com.start.agent.repository.ChapterFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChapterFactService {
    private final ChapterFactRepository chapterFactRepository;

    public ChapterFactService(ChapterFactRepository chapterFactRepository) {
        this.chapterFactRepository = chapterFactRepository;
    }

    @Transactional
    public void rebuildFactsForChapter(Long novelId, Integer chapterNumber, String content, List<String> lockedNames) {
        rebuildFactsForChapter(novelId, chapterNumber, content, lockedNames, List.of(), null);
    }

    @Transactional
    public void rebuildFactsForChapter(Long novelId, Integer chapterNumber, String content, List<String> lockedNames,
                                       List<String> sidecarFacts, String continuityAnchor) {
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
        for (ChapterFact fact : facts) {
            chapterFactRepository.save(fact);
        }
    }

    public List<ChapterFact> getFactsByNovel(Long novelId) {
        return chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId);
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
