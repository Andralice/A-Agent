package com.start.agent.service;

import com.start.agent.model.ChapterWriteState;
import com.start.agent.model.Novel;
import com.start.agent.model.NovelWritePhase;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 将「小说 / 章节是否正在重写、续写」持久化到数据库，供多端监控；
 * 与 {@link RegenerationTaskGuardService} 进程内锁配合使用（重启后仅存 DB 的一侧会由冷启动对齐）。
 */
@Slf4j
@Service
public class WritingProgressService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;

    public WritingProgressService(NovelRepository novelRepository, ChapterRepository chapterRepository) {
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional
    public void beginOperation(Long novelId, NovelWritePhase phase, Integer rangeFrom, Integer rangeTo) {
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new IllegalArgumentException("novel not found: " + novelId));
        LocalDateTime now = LocalDateTime.now();
        novel.setWritePhase(phase.name());
        novel.setWriteRangeFrom(rangeFrom);
        novel.setWriteRangeTo(rangeTo);
        novel.setWriteCursorChapter(null);
        novel.setWritePhaseDetail(buildPhaseDetail(phase, rangeFrom, rangeTo));
        novel.setWriteStartedAt(now);
        novel.setWriteUpdatedAt(now);
        novelRepository.save(novel);
        if (rangeFrom != null && rangeTo != null) {
            int lo = Math.min(rangeFrom, rangeTo);
            int hi = Math.max(rangeFrom, rangeTo);
            int n = chapterRepository.bulkSetWriteStateInRange(novelId, lo, hi, ChapterWriteState.RANGE_RESERVED.name());
            log.debug("小说 {} 区间 [{}-{}] 已批量标记 RANGE_RESERVED（影响 {} 行）", novelId, lo, hi, n);
        }
    }

    /** 光标进入某一章的正文流水线（单次 generateChapter）。 */
    @Transactional
    public void onChapterGenerationStart(Long novelId, int chapterNumber) {
        novelRepository.findById(novelId).ifPresent(novel -> {
            novel.setWriteCursorChapter(chapterNumber);
            novel.setWriteUpdatedAt(LocalDateTime.now());
            novelRepository.save(novel);
        });
        chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber).ifPresent(ch -> {
            ch.setWriteState(ChapterWriteState.GENERATING_ACTIVE.name());
            chapterRepository.save(ch);
        });
    }

    /** 单次章节生成收尾：落库 READY，区间中其它 RESERVED 保持不变直到整次任务 finish。 */
    @Transactional
    public void onChapterGenerationEnd(Long novelId, int chapterNumber) {
        chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber).ifPresent(ch -> {
            ch.setWriteState(ChapterWriteState.READY.name());
            chapterRepository.save(ch);
        });
        novelRepository.findById(novelId).ifPresent(novel -> {
            novel.setWriteUpdatedAt(LocalDateTime.now());
            novelRepository.save(novel);
        });
    }

    @Transactional
    public void finishOperation(Long novelId) {
        novelRepository.findById(novelId).ifPresent(novel -> {
            novel.setWritePhase(NovelWritePhase.IDLE.name());
            novel.setWriteRangeFrom(null);
            novel.setWriteRangeTo(null);
            novel.setWriteCursorChapter(null);
            novel.setWritePhaseDetail(null);
            novel.setWriteStartedAt(null);
            novel.setWriteUpdatedAt(LocalDateTime.now());
            novelRepository.save(novel);
        });
        chapterRepository.resetWriteStateForNovel(novelId, ChapterWriteState.READY.name());
    }

    /** 实例冷启动：清空所有 persisted 工作状态，避免出现「后端已停机但前端仍读到 REGENERATING」的假 busy。 */
    @Transactional
    public void reconcileOnColdStart() {
        log.warn("【写作状态】进程冷启动对齐：将全部小说工作台与章节占位标记复位为 IDLE/READY（若曾在写入中停机，请以章节内容为准复盘）");
        for (Novel novel : novelRepository.findAll()) {
            if (!NovelWritePhase.IDLE.name().equals(novel.getWritePhase())) {
                novel.setWritePhase(NovelWritePhase.IDLE.name());
                novel.setWriteRangeFrom(null);
                novel.setWriteRangeTo(null);
                novel.setWriteCursorChapter(null);
                novel.setWritePhaseDetail(null);
                novel.setWriteStartedAt(null);
                novel.setWriteUpdatedAt(LocalDateTime.now());
                novelRepository.save(novel);
            }
        }
        chapterRepository.resetWriteStateGlobally(ChapterWriteState.READY.name());
    }

    private static String buildPhaseDetail(NovelWritePhase phase, Integer from, Integer to) {
        if (phase == null) return "";
        return switch (phase) {
            case IDLE -> "";
            case INITIAL_BOOTSTRAP -> "outline+profile+bootstrap_chapters";
            case SINGLE_CONTINUE -> from != null ? "continue#" + from : "continue";
            case AUTO_CONTINUE_RANGE ->
                    (from != null && to != null) ? ("auto_continue " + Math.min(from, to) + "→" + Math.max(from, to)) : "auto_continue";
            case REGENERATING_RANGE ->
                    (from != null && to != null) ? ("regenerate " + Math.min(from, to) + "-" + Math.max(from, to)) : "regenerate";
            case CHARACTER_MAINTENANCE -> "character_profile_repair";
        };
    }
}
