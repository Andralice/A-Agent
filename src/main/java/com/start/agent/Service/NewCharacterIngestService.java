package com.start.agent.service;

import com.start.agent.agent.NovelGenerationAgent;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.Novel;
import com.start.agent.model.WritingPipeline;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 从 sidecar_entity 统计“近期多次出现的新角色”，并用严格 JSON 输出补全角色设定后写入角色表（partial 模式）。
 */
@Slf4j
@Service
public class NewCharacterIngestService {

    private final ChapterFactRepository chapterFactRepository;
    private final ChapterRepository chapterRepository;
    private final NovelRepository novelRepository;
    private final CharacterProfileService characterProfileService;
    private final NovelGenerationAgent generationAgent;

    /** 统计窗口：近 N 章。 */
    private final int windowChapters;
    /** 达到次数阈值后才入库。 */
    private final int minMentions;
    /** 每次最多补全多少个新角色，防止一次爆太多 AI 调用内容。 */
    private final int maxIngestPerRun;

    public NewCharacterIngestService(ChapterFactRepository chapterFactRepository,
                                     ChapterRepository chapterRepository,
                                     NovelRepository novelRepository,
                                     CharacterProfileService characterProfileService,
                                     NovelGenerationAgent generationAgent,
                                     @Value("${novel.new-characters.window-chapters:8}") int windowChapters,
                                     @Value("${novel.new-characters.min-mentions:2}") int minMentions,
                                     @Value("${novel.new-characters.max-per-run:3}") int maxIngestPerRun) {
        this.chapterFactRepository = chapterFactRepository;
        this.chapterRepository = chapterRepository;
        this.novelRepository = novelRepository;
        this.characterProfileService = characterProfileService;
        this.generationAgent = generationAgent;
        this.windowChapters = Math.max(3, windowChapters);
        this.minMentions = Math.max(2, minMentions);
        this.maxIngestPerRun = Math.max(1, maxIngestPerRun);
    }

    /**
     * 在生成/重生某一章后调用。若无候选则不触发 AI。
     *
     * @param novelId 本书
     * @param currentChapter 当前章节号
     * @param lockedNames 当前强锁角色名（不应当被当作“新角色”补全）
     */
    public void maybeIngest(Long novelId, int currentChapter, List<String> lockedNames) {
        if (novelId == null || currentChapter <= 0) return;
        int from = Math.max(1, currentChapter - windowChapters + 1);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : chapterFactRepository.countBySubjectNameSince(novelId, "sidecar_entity", from)) {
            if (row == null || row.length < 2) continue;
            String name = row[0] == null ? null : String.valueOf(row[0]).trim();
            Long cnt = row[1] instanceof Number ? ((Number) row[1]).longValue() : null;
            if (name == null || name.isBlank() || cnt == null) continue;
            counts.put(name, cnt);
        }
        if (counts.isEmpty()) return;

        Set<String> locked = new HashSet<>();
        if (lockedNames != null) for (String n : lockedNames) if (n != null && !n.isBlank()) locked.add(n.trim());

        List<CharacterProfile> existingProfiles = characterProfileService.getProfiles(novelId);
        Set<String> existingNames = new HashSet<>();
        for (CharacterProfile p : existingProfiles) {
            if (p == null || p.getCharacterName() == null) continue;
            existingNames.add(p.getCharacterName().trim());
        }

        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() < minMentions) continue;
            String name = e.getKey();
            if (locked.contains(name)) continue;
            if (existingNames.contains(name)) continue;
            // 太短/太泛化的词不入库（防止“长老/师兄/掌柜”之类）
            if (name.length() < 2 || name.length() > 10) continue;
            if (name.contains("长老") || name.contains("师兄") || name.contains("师姐") || name.contains("弟子")) continue;
            candidates.add(name);
            if (candidates.size() >= maxIngestPerRun) break;
        }
        if (candidates.isEmpty()) return;

        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) return;
        WritingPipeline pipeline;
        try {
            pipeline = novel.getWritingPipeline() == null ? WritingPipeline.POWER_FANTASY : WritingPipeline.valueOf(novel.getWritingPipeline());
        } catch (Exception ignore) {
            pipeline = WritingPipeline.POWER_FANTASY;
        }

        String promptSetting = buildNewCharacterSetting(novel, candidates);
        try {
            String profileJson = generationAgent.generateCharacterProfile(novel.getTopic(), promptSetting, pipeline);
            int saved = characterProfileService.saveCharacterProfilesJsonWithMode(novelId, profileJson, false, candidates);
            if (saved > 0) {
                log.info("新增角色已入库 novelId={}, candidates={}, saved={}", novelId, candidates, saved);
            } else {
                log.warn("新增角色补全未写入 novelId={}, candidates={}", novelId, candidates);
            }
        } catch (Exception e) {
            log.warn("新增角色补全失败 novelId={}, candidates={}, err={}", novelId, candidates, e.getMessage());
        }
    }

    private String buildNewCharacterSetting(Novel novel, List<String> candidates) {
        StringBuilder builder = new StringBuilder();
        if (novel.getGenerationSetting() != null && !novel.getGenerationSetting().isBlank()) {
            builder.append(novel.getGenerationSetting().trim()).append("\n\n");
        }
        builder.append("【新增角色补全】\n");
        builder.append("- 仅补全以下新角色（必须逐一出现，不要额外生成其他角色）：").append(String.join("、", candidates)).append("\n");
        builder.append("- 类型通常为 配角/反派/路人关键角色（按剧情功能判断），不要把他们写成主角/女主。\n");
        builder.append("- 只输出严格 JSON，字段仅 characters/name/type/want/fear/knowledge/summary。\n");
        builder.append("\n【已有大纲（必须参考）】\n").append(novel.getDescription() == null ? "无" : novel.getDescription()).append("\n");
        builder.append("\n【已有角色档案（必须参考，避免重名与冲突）】\n").append(characterProfileService.getCharacterProfileFromDatabase(novel.getId()));
        return builder.toString();
    }
}

