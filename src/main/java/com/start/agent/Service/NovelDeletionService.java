package com.start.agent.service;

import com.start.agent.model.GenerationTask;
import com.start.agent.model.GenerationTaskStatus;
import com.start.agent.model.Novel;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.ConsistencyAlertRepository;
import com.start.agent.repository.GenerationLogRepository;
import com.start.agent.repository.GenerationTaskRepository;
import com.start.agent.repository.NovelRepository;
import com.start.agent.repository.PlotSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 永久删除小说及其关联数据；须文本确认书名与固定短语，避免误删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelDeletionService {

    /** 前端须提示用户原样抄写（含标点）。 */
    public static final String REQUIRED_PHRASE = "我确认永久删除此书";

    private final NovelRepository novelRepository;
    private final GenerationTaskRepository generationTaskRepository;
    private final GenerationTaskService generationTaskService;
    private final ChapterFactRepository chapterFactRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final GenerationLogRepository generationLogRepository;
    private final ConsistencyAlertRepository consistencyAlertRepository;
    private final PlotSnapshotRepository plotSnapshotRepository;

    /**
     * @param confirmTitle       须与当前书名 trim 后完全一致
     * @param typedPhrase        须等于 {@link #REQUIRED_PHRASE}
     * @param acknowledgeIrreversible 须为 true
     */
    @Transactional
    public void deleteNovelPermanently(Long novelId, String confirmTitle, String typedPhrase, Boolean acknowledgeIrreversible) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("小说不存在"));
        validateConfirmation(novel, confirmTitle, typedPhrase, acknowledgeIrreversible);

        for (GenerationTask t : generationTaskRepository.findByNovelIdOrderByCreateTimeDesc(novelId)) {
            String st = t.getStatus();
            if (GenerationTaskStatus.PENDING.name().equals(st) || GenerationTaskStatus.RUNNING.name().equals(st)) {
                generationTaskService.cancelTask(t.getId());
            }
        }

        chapterFactRepository.deleteAllByNovelId(novelId);
        consistencyAlertRepository.deleteAllByNovelId(novelId);
        plotSnapshotRepository.deleteAllByNovelId(novelId);
        generationLogRepository.deleteAllByNovelId(novelId);
        generationTaskRepository.deleteAllByNovelId(novelId);
        characterProfileRepository.deleteByNovelId(novelId);
        chapterRepository.deleteAllByNovelId(novelId);
        novelRepository.delete(novel);

        log.warn("小说已永久删除 novelId={} title={}", novelId, novel.getTitle());
    }

    private void validateConfirmation(Novel novel, String confirmTitle, String typedPhrase, Boolean acknowledgeIrreversible) {
        if (!Boolean.TRUE.equals(acknowledgeIrreversible)) {
            throw new IllegalArgumentException("请勾选「我已知晓删除后无法恢复」后再提交。");
        }
        String expectedTitle = novel.getTitle() == null ? "" : novel.getTitle().trim();
        String gotTitle = confirmTitle == null ? "" : confirmTitle.trim();
        if (!Objects.equals(expectedTitle, gotTitle)) {
            throw new IllegalArgumentException("书名与确认输入不一致，请完整复制当前书名。");
        }
        String phrase = typedPhrase == null ? "" : typedPhrase.trim();
        if (!REQUIRED_PHRASE.equals(phrase)) {
            throw new IllegalArgumentException("确认语句不正确，请原样填写：「" + REQUIRED_PHRASE + "」。");
        }
    }
}
