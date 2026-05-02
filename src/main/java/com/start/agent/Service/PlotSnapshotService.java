package com.start.agent.service;

import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.PlotSnapshot;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.PlotSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** 在关键章节节点生成/刷新 {@link com.start.agent.model.PlotSnapshot}，压缩长篇主线信息。 */
@Service
public class PlotSnapshotService {
    private final PlotSnapshotRepository plotSnapshotRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterFactRepository chapterFactRepository;

    public PlotSnapshotService(PlotSnapshotRepository plotSnapshotRepository,
                               ChapterRepository chapterRepository,
                               ChapterFactRepository chapterFactRepository) {
        this.plotSnapshotRepository = plotSnapshotRepository;
        this.chapterRepository = chapterRepository;
        this.chapterFactRepository = chapterFactRepository;
    }

    public String getLatestSnapshotBlock(Long novelId) {
        Optional<PlotSnapshot> snapshot = plotSnapshotRepository.findTopByNovelIdOrderBySnapshotChapterDesc(novelId);
        if (snapshot.isEmpty()) return "【阶段主线快照】\n无";
        PlotSnapshot s = snapshot.get();
        return "【阶段主线快照（截至第" + s.getSnapshotChapter() + "章）】\n" + s.getSnapshotContent();
    }

    public String detectSnapshotDrift(Long novelId, String chapterContent, List<String> lockedNames) {
        Optional<PlotSnapshot> snapshotOpt = plotSnapshotRepository.findTopByNovelIdOrderBySnapshotChapterDesc(novelId);
        if (snapshotOpt.isEmpty() || chapterContent == null || chapterContent.isBlank()) return null;

        PlotSnapshot snapshot = snapshotOpt.get();
        long appears = lockedNames.stream().filter(name -> name != null && !name.isBlank() && chapterContent.contains(name)).count();
        if (!lockedNames.isEmpty() && appears == 0) {
            return "当前章节与阶段快照可能脱节：核心角色全部缺席。请确保主线与角色状态延续到本章。";
        }

        String[] anchors = snapshot.getSnapshotContent().split("\n");
        int matched = 0;
        for (String anchor : anchors) {
            String trimmed = anchor.trim();
            if (trimmed.length() < 8 || !trimmed.startsWith("-")) continue;
            String token = trimmed.replace("- ", "");
            token = token.length() > 16 ? token.substring(0, 16) : token;
            if (!token.isBlank() && chapterContent.contains(token)) matched++;
        }
        if (matched == 0) {
            return "当前章节未承接阶段快照中的关键主线线索，请补充与最近阶段目标的因果连接。";
        }
        return null;
    }

    @Transactional
    public void refreshSnapshotIfNeeded(Long novelId, int chapterNumber, List<String> lockedNames) {
        if (chapterNumber <= 0 || chapterNumber % 5 != 0) return;
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
        if (chapters.isEmpty()) return;

        int fromChapter = Math.max(1, chapterNumber - 4);
        List<Chapter> window = chapters.stream()
                .filter(ch -> ch.getChapterNumber() != null && ch.getChapterNumber() >= fromChapter && ch.getChapterNumber() <= chapterNumber)
                .toList();
        List<ChapterFact> facts = chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId).stream()
                .filter(f -> f.getChapterNumber() != null && f.getChapterNumber() >= fromChapter && f.getChapterNumber() <= chapterNumber)
                .toList();

        PlotSnapshot snapshot = new PlotSnapshot();
        snapshot.setNovelId(novelId);
        snapshot.setSnapshotChapter(chapterNumber);
        snapshot.setKeyCharacters(String.join(",", lockedNames));
        snapshot.setSnapshotContent(buildSnapshotContent(window, facts, lockedNames, fromChapter, chapterNumber));
        plotSnapshotRepository.save(snapshot);
    }

    private String buildSnapshotContent(List<Chapter> chapters, List<ChapterFact> facts, List<String> lockedNames, int fromChapter, int toChapter) {
        StringBuilder builder = new StringBuilder();
        builder.append("- 阶段范围: 第").append(fromChapter).append("章 ~ 第").append(toChapter).append("章\n");
        if (!lockedNames.isEmpty()) builder.append("- 核心角色: ").append(String.join("、", lockedNames)).append("\n");

        List<String> chapterHooks = facts.stream()
                .filter(f -> "chapter_hook".equals(f.getFactType()))
                .map(ChapterFact::getFactContent)
                .filter(v -> v != null && !v.isBlank())
                .limit(5)
                .collect(Collectors.toList());
        if (!chapterHooks.isEmpty()) {
            builder.append("- 阶段主线线索:\n");
            for (String hook : chapterHooks) builder.append("  - ").append(clip(hook, 120)).append("\n");
        }

        List<String> characterFacts = facts.stream()
                .filter(f -> "character_state".equals(f.getFactType()))
                .map(f -> (f.getSubjectName() == null ? "角色" : f.getSubjectName()) + ": " + clip(f.getFactContent(), 80))
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
        if (!characterFacts.isEmpty()) {
            builder.append("- 角色阶段状态:\n");
            for (String line : characterFacts) builder.append("  - ").append(line).append("\n");
        }

        if (!chapters.isEmpty()) {
            Chapter last = chapters.get(chapters.size() - 1);
            builder.append("- 最近收束点: ").append(last.getTitle()).append(" / ").append(clip(last.getContent(), 100)).append("\n");
        }
        return builder.toString().trim();
    }

    private String clip(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replace("\n", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
