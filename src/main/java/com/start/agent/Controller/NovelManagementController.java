package com.start.agent.controller;

import com.start.agent.model.Chapter;
import com.start.agent.model.ChapterFact;
import com.start.agent.model.ChapterRevision;
import com.start.agent.model.ChapterWriteState;
import com.start.agent.model.OutlineRevision;
import com.start.agent.model.CharacterProfileRevision;
import com.start.agent.model.CharacterProfile;
import com.start.agent.model.ConsistencyAlert;
import com.start.agent.model.GenerationLog;
import com.start.agent.model.GenerationTask;
import com.start.agent.model.Novel;
import com.start.agent.model.NovelCharacterState;
import com.start.agent.model.NovelWritePhase;
import com.start.agent.model.WritingPipeline;
import com.start.agent.repository.ChapterFactRepository;
import com.start.agent.repository.ChapterRevisionRepository;
import com.start.agent.repository.OutlineRevisionRepository;
import com.start.agent.repository.CharacterProfileRepository;
import com.start.agent.repository.CharacterProfileRevisionRepository;
import com.start.agent.repository.ConsistencyAlertRepository;
import com.start.agent.repository.GenerationLogRepository;
import com.start.agent.repository.GenerationTaskRepository;
import com.start.agent.repository.NovelRepository;
import com.start.agent.repository.PlotSnapshotRepository;
import com.start.agent.service.CharacterNarrativeStateService;
import com.start.agent.service.NovelAgentService;
import com.start.agent.service.NovelDeletionService;
import com.start.agent.service.NovelExportService;
import com.start.agent.service.NovelLibraryAccessService;
import com.start.agent.service.GenerationTaskService;
import com.start.agent.service.RegenerationTaskGuardService;
import com.start.agent.exception.NotFoundException;
import com.start.agent.exception.UserFriendlyExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * HTTP API：小说 CRUD、开书/续写/自动续写、重生、导出、进度与监控、流水线切换等。
 */
@Slf4j
@RestController
@RequestMapping("/api/novel")
@CrossOrigin(origins = "*")
public class NovelManagementController {
    private static final List<String> ACTIVE_TASK_STATUSES = List.of("PENDING", "RUNNING");
    private static final int OUTLINE_REGENERATION_HINT_MAX_LEN = 12_000;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NovelAgentService agentService;
    @Autowired private NovelRepository novelRepository;
    @Autowired private CharacterProfileRepository characterProfileRepository;
    @Autowired private GenerationLogRepository generationLogRepository;
    @Autowired private ConsistencyAlertRepository consistencyAlertRepository;
    @Autowired private ChapterFactRepository chapterFactRepository;
    @Autowired private PlotSnapshotRepository plotSnapshotRepository;
    @Autowired private NovelExportService novelExportService;
    @Autowired private RegenerationTaskGuardService regenerationTaskGuardService;
    @Autowired private GenerationTaskService generationTaskService;
    @Autowired private GenerationTaskRepository generationTaskRepository;
    @Autowired private NovelDeletionService novelDeletionService;
    @Autowired private NovelLibraryAccessService novelLibraryAccessService;
    @Autowired private CharacterNarrativeStateService characterNarrativeStateService;
    @Autowired private ChapterRevisionRepository chapterRevisionRepository;
    @Autowired private OutlineRevisionRepository outlineRevisionRepository;
    @Autowired private CharacterProfileRevisionRepository characterProfileRevisionRepository;
    @Autowired private com.start.agent.repository.ChapterRepository chapterRepository;
    @Autowired private com.start.agent.service.StrandWeaveService strandWeaveService;
    @Autowired private com.start.agent.service.ForeshadowingService foreshadowingService;
    @Autowired private com.start.agent.service.ReadingPowerService readingPowerService;
    @Autowired private com.start.agent.service.StoryContractService storyContractService;
    @Autowired private com.start.agent.service.ChapterCommitService chapterCommitService;
    @Autowired private com.start.agent.service.StructuredOutlineService structuredOutlineService;
    @Autowired private com.start.agent.service.AiFlavorDetector aiFlavorDetector;
    @Autowired private com.start.agent.service.WritingKnowledgeService writingKnowledgeService;
    @Autowired private com.start.agent.repository.ForeshadowingRepository foreshadowingRepository;
    @Autowired private com.start.agent.repository.ChapterReadingPowerRepository chapterReadingPowerRepository;
    @Autowired private com.start.agent.repository.StoryContractRepository storyContractRepository;
    @Autowired @Qualifier("generationExecutor") private Executor generationExecutor;

    private void guardRead(Long novelId) {
        novelLibraryAccessService.assertCanRead(novelId);
    }

    private void guardTask(Long taskId) {
        GenerationTask t = generationTaskRepository.findById(taskId).orElseThrow(() -> new NotFoundException("未找到该任务"));
        novelLibraryAccessService.assertCanRead(t.getNovelId());
    }

    @GetMapping("/list")
    public List<Novel> listNovels() {
        log.info("【API请求】获取小说列表");
        return novelLibraryAccessService.listNovelsForCaller();
    }

    @GetMapping("/{novelId}/chapters")
    public List<Chapter> getChapters(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的章节列表", novelId);
        return agentService.getChaptersByNovelId(novelId);
    }

    // ── 章节版本管理 ──

    /** 更新章节内容（手动编辑）。自动将旧内容存档为 revision，返回新版本号。 */
    @PutMapping("/{novelId}/chapters/{chapterNumber}/content")
    public Map<String, Object> updateChapterContent(@PathVariable Long novelId, @PathVariable int chapterNumber,
                                                    @RequestBody UpdateContentRequest request) {
        guardRead(novelId);
        Chapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
                .orElseThrow(() -> new NotFoundException("未找到该章节"));
        // 存档旧版本
        int nextRev = chapterRevisionRepository.countByNovelIdAndChapterNumber(novelId, chapterNumber) + 1;
        ChapterRevision rev = new ChapterRevision(novelId, chapterNumber, nextRev,
                chapter.getContent(), chapter.getTitle(), request.getEditNote());
        chapterRevisionRepository.save(rev);
        // 写入新内容
        chapter.setContent(request.getContent());
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            chapter.setTitle(request.getTitle().trim());
        }
        chapterRepository.save(chapter);
        log.info("【版本管理】小说 {} 第 {} 章手动编辑，旧版存档为 revision {}，编辑备注: {}",
                novelId, chapterNumber, nextRev, request.getEditNote());
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success");
        data.put("code", "OK");
        data.put("revisionNumber", nextRev);
        data.put("message", "内容已更新，旧版已存档为 revision " + nextRev);
        return data;
    }

    /** 获取某章的全部历史版本列表（倒序，最新在前）。 */
    @GetMapping("/{novelId}/chapters/{chapterNumber}/revisions")
    public List<Map<String, Object>> listRevisions(@PathVariable Long novelId, @PathVariable int chapterNumber) {
        guardRead(novelId);
        return chapterRevisionRepository.findByNovelIdAndChapterNumberOrderByRevisionNumberDesc(novelId, chapterNumber)
                .stream().map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("revisionNumber", r.getRevisionNumber());
                    m.put("title", r.getTitle());
                    m.put("contentLength", r.getContent() == null ? 0 : r.getContent().length());
                    m.put("editNote", r.getEditNote());
                    m.put("createTime", r.getCreateTime());
                    return m;
                }).collect(Collectors.toList());
    }

    /** 获取某个版本的完整内容。 */
    @GetMapping("/{novelId}/chapters/{chapterNumber}/revisions/{revisionId}")
    public ChapterRevision getRevision(@PathVariable Long novelId, @PathVariable int chapterNumber,
                                       @PathVariable Long revisionId) {
        guardRead(novelId);
        return chapterRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId) && r.getChapterNumber().equals(chapterNumber))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
    }

    /** 回滚到指定版本（自动将当前内容存档后再恢复）。 */
    @PostMapping("/{novelId}/chapters/{chapterNumber}/revisions/{revisionId}/restore")
    public Map<String, Object> restoreRevision(@PathVariable Long novelId, @PathVariable int chapterNumber,
                                               @PathVariable Long revisionId) {
        guardRead(novelId);
        ChapterRevision targetRev = chapterRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId) && r.getChapterNumber().equals(chapterNumber))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
        Chapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
                .orElseThrow(() -> new NotFoundException("未找到该章节"));
        // 存档当前内容
        int nextRev = chapterRevisionRepository.countByNovelIdAndChapterNumber(novelId, chapterNumber) + 1;
        ChapterRevision currentRev = new ChapterRevision(novelId, chapterNumber, nextRev,
                chapter.getContent(), chapter.getTitle(), "回滚到 revision " + targetRev.getRevisionNumber() + " 前的自动存档");
        chapterRevisionRepository.save(currentRev);
        // 恢复目标版本
        chapter.setContent(targetRev.getContent());
        if (targetRev.getTitle() != null && !targetRev.getTitle().isBlank()) {
            chapter.setTitle(targetRev.getTitle());
        }
        chapterRepository.save(chapter);
        log.info("【版本管理】小说 {} 第 {} 章回滚到 revision {}（当前内容存档为 revision {}）",
                novelId, chapterNumber, targetRev.getRevisionNumber(), nextRev);
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success");
        data.put("code", "OK");
        data.put("restoredFromRevision", targetRev.getRevisionNumber());
        data.put("previousSavedAsRevision", nextRev);
        data.put("message", "已回滚到 revision " + targetRev.getRevisionNumber());
        return data;
    }

    // ── 大纲版本管理 ──

    @PutMapping("/{novelId}/outline/content")
    public Map<String, Object> updateOutlineContent(@PathVariable Long novelId, @RequestBody UpdateContentRequest request) {
        guardRead(novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new NotFoundException("未找到该小说"));
        int nextRev = outlineRevisionRepository.countByNovelId(novelId) + 1;
        OutlineRevision rev = new OutlineRevision(novelId, nextRev, novel.getDescription(), request.getEditNote());
        outlineRevisionRepository.save(rev);
        novel.setDescription(request.getContent());
        novelRepository.save(novel);
        log.info("【版本管理】小说 {} 大纲手动编辑，旧版存档为 revision {}", novelId, nextRev);
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success"); data.put("code", "OK");
        data.put("revisionNumber", nextRev);
        data.put("message", "大纲已更新，旧版已存档为 revision " + nextRev);
        return data;
    }

    @GetMapping("/{novelId}/outline/revisions")
    public List<Map<String, Object>> listOutlineRevisions(@PathVariable Long novelId) {
        guardRead(novelId);
        return outlineRevisionRepository.findByNovelIdOrderByRevisionNumberDesc(novelId).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("revisionNumber", r.getRevisionNumber());
            m.put("contentLength", r.getContent() == null ? 0 : r.getContent().length());
            m.put("editNote", r.getEditNote()); m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{novelId}/outline/revisions/{revisionId}")
    public OutlineRevision getOutlineRevision(@PathVariable Long novelId, @PathVariable Long revisionId) {
        guardRead(novelId);
        return outlineRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
    }

    @PostMapping("/{novelId}/outline/revisions/{revisionId}/restore")
    public Map<String, Object> restoreOutlineRevision(@PathVariable Long novelId, @PathVariable Long revisionId) {
        guardRead(novelId);
        OutlineRevision targetRev = outlineRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
        Novel novel = novelRepository.findById(novelId).orElseThrow(() -> new NotFoundException("未找到该小说"));
        int nextRev = outlineRevisionRepository.countByNovelId(novelId) + 1;
        outlineRevisionRepository.save(new OutlineRevision(novelId, nextRev, novel.getDescription(),
                "回滚到 revision " + targetRev.getRevisionNumber() + " 前的自动存档"));
        novel.setDescription(targetRev.getContent());
        novelRepository.save(novel);
        log.info("【版本管理】小说 {} 大纲回滚到 revision {}", novelId, targetRev.getRevisionNumber());
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success"); data.put("code", "OK");
        data.put("restoredFromRevision", targetRev.getRevisionNumber());
        data.put("previousSavedAsRevision", nextRev);
        data.put("message", "大纲已回滚到 revision " + targetRev.getRevisionNumber());
        return data;
    }

    // ── 角色设定版本管理 ──

    @PutMapping("/{novelId}/characters/{characterId}/content")
    public Map<String, Object> updateCharacterProfileContent(@PathVariable Long novelId, @PathVariable Long characterId,
                                                              @RequestBody UpdateContentRequest request) {
        guardRead(novelId);
        CharacterProfile cp = characterProfileRepository.findById(characterId)
                .filter(c -> c.getNovelId().equals(novelId))
                .orElseThrow(() -> new NotFoundException("未找到该角色"));
        int nextRev = characterProfileRevisionRepository.countByNovelIdAndCharacterProfileId(novelId, characterId) + 1;
        CharacterProfileRevision rev = new CharacterProfileRevision(novelId, characterId, nextRev,
                cp.getCharacterName(), cp.getProfileContent(), request.getEditNote());
        characterProfileRevisionRepository.save(rev);
        cp.setProfileContent(request.getContent());
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            cp.setCharacterName(request.getTitle().trim());
        }
        characterProfileRepository.save(cp);
        log.info("【版本管理】小说 {} 角色 {} 手动编辑，旧版存档为 revision {}", novelId, cp.getCharacterName(), nextRev);
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success"); data.put("code", "OK");
        data.put("revisionNumber", nextRev);
        data.put("message", "角色设定已更新，旧版已存档为 revision " + nextRev);
        return data;
    }

    @GetMapping("/{novelId}/characters/{characterId}/revisions")
    public List<Map<String, Object>> listCharacterProfileRevisions(@PathVariable Long novelId, @PathVariable Long characterId) {
        guardRead(novelId);
        return characterProfileRevisionRepository.findByNovelIdAndCharacterProfileIdOrderByRevisionNumberDesc(novelId, characterId)
                .stream().map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId()); m.put("revisionNumber", r.getRevisionNumber());
                    m.put("characterName", r.getCharacterName());
                    m.put("contentLength", r.getContent() == null ? 0 : r.getContent().length());
                    m.put("editNote", r.getEditNote()); m.put("createTime", r.getCreateTime());
                    return m;
                }).collect(Collectors.toList());
    }

    @GetMapping("/{novelId}/characters/{characterId}/revisions/{revisionId}")
    public CharacterProfileRevision getCharacterProfileRevision(@PathVariable Long novelId, @PathVariable Long characterId,
                                                                 @PathVariable Long revisionId) {
        guardRead(novelId);
        return characterProfileRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId) && r.getCharacterProfileId().equals(characterId))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
    }

    @PostMapping("/{novelId}/characters/{characterId}/revisions/{revisionId}/restore")
    public Map<String, Object> restoreCharacterProfileRevision(@PathVariable Long novelId, @PathVariable Long characterId,
                                                                @PathVariable Long revisionId) {
        guardRead(novelId);
        CharacterProfileRevision targetRev = characterProfileRevisionRepository.findById(revisionId)
                .filter(r -> r.getNovelId().equals(novelId) && r.getCharacterProfileId().equals(characterId))
                .orElseThrow(() -> new NotFoundException("未找到该版本"));
        CharacterProfile cp = characterProfileRepository.findById(characterId)
                .filter(c -> c.getNovelId().equals(novelId))
                .orElseThrow(() -> new NotFoundException("未找到该角色"));
        int nextRev = characterProfileRevisionRepository.countByNovelIdAndCharacterProfileId(novelId, characterId) + 1;
        characterProfileRevisionRepository.save(new CharacterProfileRevision(novelId, characterId, nextRev,
                cp.getCharacterName(), cp.getProfileContent(),
                "回滚到 revision " + targetRev.getRevisionNumber() + " 前的自动存档"));
        cp.setProfileContent(targetRev.getContent());
        if (targetRev.getCharacterName() != null && !targetRev.getCharacterName().isBlank()) {
            cp.setCharacterName(targetRev.getCharacterName());
        }
        characterProfileRepository.save(cp);
        log.info("【版本管理】小说 {} 角色 {} 回滚到 revision {}", novelId, cp.getCharacterName(), targetRev.getRevisionNumber());
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success"); data.put("code", "OK");
        data.put("restoredFromRevision", targetRev.getRevisionNumber());
        data.put("previousSavedAsRevision", nextRev);
        data.put("message", "角色设定已回滚到 revision " + targetRev.getRevisionNumber());
        return data;
    }

    // ── 原有接口 ──

    @GetMapping("/{novelId}")
    public Novel getNovel(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的基础信息", novelId);
        return novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
    }

    /**
     * 删除前由前端拉取：书名、须抄写的确认句、进行中任务数量等（不把删除做成 GET 副作用）。
     */
    @GetMapping("/{novelId}/delete-guard")
    public Map<String, Object> deleteGuard(@PathVariable Long novelId) {
        guardRead(novelId);
        novelLibraryAccessService.assertCanDelete(novelId);
        Novel n = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        int activeTasks = generationTaskRepository.findByNovelIdAndStatusInOrderByCreateTimeAsc(novelId, ACTIVE_TASK_STATUSES).size();
        Map<String, Object> data = new HashMap<>();
        data.put("novelId", novelId);
        data.put("title", n.getTitle());
        data.put("requiredPhrase", NovelDeletionService.REQUIRED_PHRASE);
        data.put("activeTaskCount", activeTasks);
        data.put("hint", "须完整输入当前书名、原样抄写 requiredPhrase，并勾选知晓不可恢复；提交后会取消进行中任务并永久清除本书全部章节与关联数据。");
        return success("请展示确认表单后再提交删除", data);
    }

    /**
     * 永久删除小说（多步确认由请求体承载，避免误触）。
     */
    @PostMapping("/{novelId}/delete")
    public Map<String, Object> deleteNovel(@PathVariable Long novelId, @RequestBody(required = false) DeleteNovelRequest req) {
        guardRead(novelId);
        novelLibraryAccessService.assertCanDelete(novelId);
        try {
            novelDeletionService.deleteNovelPermanently(
                    novelId,
                    req == null ? null : req.getConfirmTitle(),
                    req == null ? null : req.getTypedPhrase(),
                    req == null ? null : req.getAcknowledgeIrreversible());
            return success("小说及其关联数据已永久删除", new HashMap<>(Map.of("novelId", novelId)));
        } catch (IllegalArgumentException e) {
            return error("DELETE_CONFIRM_MISMATCH", e.getMessage());
        }
    }

    @GetMapping("/{novelId}/outline")
    public Map<String, Object> getOutline(@PathVariable Long novelId) {
        guardRead(novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novel.getId());
        result.put("title", novel.getTitle());
        result.put("topic", novel.getTopic());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("outline", novel.getDescription());
        result.put("outlineGraphJson", novel.getOutlineGraphJson());
        result.put("serializationPlatform", novel.getSerializationPlatform());
        result.put("creatorNote", novel.getCreatorNote());
        result.put("ready", novel.getDescription() != null && !novel.getDescription().isBlank() && !"AI generated novel".equals(novel.getDescription()) && !"AI生成的爽文小说".equals(novel.getDescription()));
        result.put("updateTime", novel.getUpdateTime());
        return result;
    }

    /**
     * 重新生成 AI 大纲（覆盖 {@link Novel#getDescription()}）。同步调用，耗时可较长；请求体可带写作建议。
     * 已发布章节正文不会自动改写，可能与新旧大纲不一致。
     */
    @PostMapping("/{novelId}/outline/regenerate")
    public Map<String, Object> regenerateOutline(@PathVariable Long novelId, @RequestBody(required = false) RegenerateOutlineRequest request) {
        guardRead(novelId);
        try {
            String hint = request == null ? null : request.getHint();
            if (hint != null && hint.length() > OUTLINE_REGENERATION_HINT_MAX_LEN) {
                return error("INVALID_ARGUMENT", "大纲建议过长（最多 " + OUTLINE_REGENERATION_HINT_MAX_LEN + " 字符）");
            }
            agentService.regenerateOutline(novelId, hint,
                    request == null ? null : request.getOutlineDetailedPrefixChapters(),
                    request == null ? null : request.getOutlineMinRoadmapChapters());
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novelId);
            return success("大纲已重新生成并写入本书（已写章节不会自动改写，必要时请手动重生相关章节）", data);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENT", UserFriendlyExceptions.mask(e));
        } catch (IllegalStateException e) {
            return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
        } catch (Exception e) {
            log.warn("大纲重新生成失败 novelId={}", novelId, e);
            return error("OUTLINE_REGENERATE_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @GetMapping("/{novelId}/pipeline")
    public Map<String, Object> getPipeline(@PathVariable Long novelId) {
        guardRead(novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("pipeline", novel.getWritingPipeline() == null ? WritingPipeline.POWER_FANTASY.name() : novel.getWritingPipeline());
        result.put("writingStyleParams", novel.getWritingStyleParams());
        result.put("serializationPlatform", novel.getSerializationPlatform());
        result.put("creatorNote", novel.getCreatorNote());
        result.put("hotMemeEnabled", novel.isHotMemeEnabled());
        result.put("libraryPublic", novel.isLibraryPublic());
        return result;
    }

    /** 更新文风微参；仅影响后续生成。空请求体表示清空已存微参。 */
    @PostMapping("/{novelId}/writing-style-params")
    public Map<String, Object> updateWritingStyleParams(@PathVariable Long novelId, @RequestBody(required = false) WritingStyleParamsRequest request) {
        guardRead(novelId);
        try {
            String json = null;
            if (request != null) {
                if (request.getWritingStyleParams() != null) {
                    json = request.getWritingStyleParams().isEmpty()
                            ? null
                            : objectMapper.writeValueAsString(request.getWritingStyleParams());
                } else if (request.getWritingStyleParamsRaw() != null) {
                    json = request.getWritingStyleParamsRaw().isBlank() ? null : request.getWritingStyleParamsRaw().trim();
                }
            }
            agentService.updateWritingStyleParams(novelId, json);
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novelId);
            data.put("writingStyleParams", novelRepository.findById(novelId).map(Novel::getWritingStyleParams).orElse(null));
            return success("文风微参已更新（仅影响后续生成）", data);
        } catch (IllegalArgumentException e) {
            return error("INVALID_STYLE_PARAMS", UserFriendlyExceptions.mask(e));
        } catch (Exception e) {
            log.warn("文风微参更新失败 novelId={}", novelId, e);
            return error("STYLE_PARAMS_UPDATE_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    /**
     * 更新「连载平台」「创作说明」；仅展示与备注，不参与 AI 生成。未传的字段不修改；传空字符串可清空该字段。
     */
    @PostMapping("/{novelId}/book-meta")
    public Map<String, Object> updateBookMeta(@PathVariable Long novelId, @RequestBody(required = false) BookMetaRequest request) {
        guardRead(novelId);
        try {
            if (request == null) {
                return error("INVALID_ARGUMENT", "请提供 JSON 体，例如 serializationPlatform、creatorNote");
            }
            agentService.updateBookMeta(novelId, request.getSerializationPlatform(), request.getCreatorNote());
            Novel n = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novelId);
            data.put("serializationPlatform", n.getSerializationPlatform());
            data.put("creatorNote", n.getCreatorNote());
            return success("书籍备注已更新", data);
        } catch (Exception e) {
            log.warn("book-meta 更新失败 novelId={}", novelId, e);
            return error("BOOK_META_UPDATE_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    /** 开关：是否在正文适当位置少量使用网络热梗（后续续写/重生章节生效；已写章节不变）。 */
    @PostMapping("/{novelId}/hot-meme")
    public Map<String, Object> updateHotMeme(@PathVariable Long novelId, @RequestBody(required = false) HotMemeRequest request) {
        guardRead(novelId);
        try {
            boolean enabled = request != null && Boolean.TRUE.equals(request.getEnabled());
            agentService.updateHotMemeEnabled(novelId, enabled);
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novelId);
            data.put("hotMemeEnabled", enabled);
            return success("网络热梗开关已更新（仅影响后续生成）", data);
        } catch (Exception e) {
            log.warn("热梗开关更新失败 novelId={}", novelId, e);
            return error("HOT_MEME_UPDATE_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{novelId}/pipeline")
    public Map<String, Object> updatePipeline(@PathVariable Long novelId, @RequestBody PipelineRequest request) {
        guardRead(novelId);
        try {
            WritingPipeline pipeline = WritingPipeline.fromPath(request == null ? null : request.getPipeline());
            agentService.updateNovelPipeline(novelId, pipeline);
            Map<String, Object> data = new HashMap<>();
            data.put("pipeline", pipeline.name());
            return success("流水线更新成功（后续章节生效）", data);
        } catch (Exception e) {
            log.warn("流水线更新失败 novelId={}", novelId, e);
            return error("PIPELINE_UPDATE_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    /** 管理员：将本书标记为书库「公共」或「仅管理员可见」（启用 app.security 时须携带管理员 JWT）。 */
    @PostMapping("/{novelId}/library-visibility")
    public Map<String, Object> updateLibraryVisibility(@PathVariable Long novelId, @RequestBody(required = false) LibraryVisibilityRequest request) {
        novelLibraryAccessService.assertAdminIfSecurityEnabled();
        if (request == null || request.getLibraryPublic() == null) {
            return error("INVALID_ARGUMENT", "请提供 JSON：{\"libraryPublic\": true|false}");
        }
        Novel n = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        n.setLibraryPublic(Boolean.TRUE.equals(request.getLibraryPublic()));
        novelRepository.save(n);
        Map<String, Object> data = new HashMap<>();
        data.put("novelId", novelId);
        data.put("libraryPublic", n.isLibraryPublic());
        return success("书库可见性已更新", data);
    }

    @GetMapping("/{novelId}/characters")
    public List<CharacterProfile> getCharacters(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的角色设定", novelId);
        return characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
    }

    @PostMapping("/{novelId}/characters/repair")
    public Map<String, Object> repairCharacters(@PathVariable Long novelId, @RequestBody(required = false) RepairCharacterRequest request) {
        guardRead(novelId);
        try {
            boolean forceRegenerate = request != null && Boolean.TRUE.equals(request.getForceRegenerate());
            String rebuildMode = request == null ? "all" : request.getRebuildMode();
            String characterContextHint = request == null ? null : request.getCharacterContextHint();
            if ((characterContextHint == null || characterContextHint.isBlank()) && request != null) {
                characterContextHint = request.getFirstSwordMaster(); // backward compatibility
            }
            List<String> targetCharacterNames = request == null ? List.of() : request.getTargetCharacterNames();
            String extraHint = request == null ? null : request.getExtraHint();
            NovelAgentService.RebuildMode mode = NovelAgentService.RebuildMode.from(rebuildMode);
            NovelAgentService.CharacterRepairOptions options =
                    new NovelAgentService.CharacterRepairOptions(forceRegenerate, mode, characterContextHint, targetCharacterNames, extraHint);
            CompletableFuture.runAsync(() -> agentService.repairCharacterProfiles(0L, novelId.intValue(), options), generationExecutor);
            Map<String, Object> data = new HashMap<>();
            data.put("forceRegenerate", forceRegenerate);
            data.put("rebuildMode", mode.name().toLowerCase());
            return success("角色设定修复任务已启动，请稍后刷新角色列表", data);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) return error("INVALID_ARGUMENT", UserFriendlyExceptions.mask(e));
            log.warn("角色修复任务启动失败 novelId={}", novelId, e);
            return error("CHARACTER_REPAIR_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @GetMapping("/{novelId}/generation-logs")
    public List<GenerationLog> getGenerationLogs(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的生成日志", novelId);
        return generationLogRepository.findByNovelId(novelId);
    }

    @GetMapping("/{novelId}/consistency-alerts")
    public List<ConsistencyAlert> getConsistencyAlerts(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的一致性告警", novelId);
        return consistencyAlertRepository.findByNovelIdOrderByCreateTimeDesc(novelId);
    }

    @GetMapping("/{novelId}/chapter-facts")
    public List<ChapterFact> getChapterFacts(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的章节事实", novelId);
        return chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId);
    }

    @GetMapping("/{novelId}/chapter-sidecar")
    public List<Map<String, Object>> getChapterSidecar(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的章节 sidecar 视图", novelId);
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        Map<Integer, Chapter> chapterMap = chapters.stream().collect(Collectors.toMap(Chapter::getChapterNumber, c -> c, (a, b) -> a));
        Map<Integer, List<ChapterFact>> grouped = chapterFactRepository.findByNovelIdOrderByChapterNumberAscCreateTimeAsc(novelId).stream()
                .filter(f -> "sidecar_fact".equals(f.getFactType()) || "continuity_anchor".equals(f.getFactType()))
                .collect(Collectors.groupingBy(ChapterFact::getChapterNumber, java.util.LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<ChapterFact>> entry : grouped.entrySet()) {
            Integer chapterNumber = entry.getKey();
            List<ChapterFact> facts = entry.getValue();
            Chapter chapter = chapterMap.get(chapterNumber);
            Map<String, Object> item = new HashMap<>();
            item.put("chapterNumber", chapterNumber);
            item.put("chapterTitle", chapter == null ? ("第" + chapterNumber + "章") : chapter.getTitle());
            item.put("facts", facts.stream().filter(f -> "sidecar_fact".equals(f.getFactType())).map(ChapterFact::getFactContent).toList());
            item.put("continuityAnchor", facts.stream().filter(f -> "continuity_anchor".equals(f.getFactType())).map(ChapterFact::getFactContent).findFirst().orElse(null));
            result.add(item);
        }
        return result;
    }

    /** M7：各章叙事引擎侧车 JSON（Planner/Lint 等）；仅包含有数据的章。 */
    @GetMapping("/{novelId}/narrative-artifacts")
    public List<Map<String, Object>> listNarrativeArtifacts(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的叙事引擎侧车列表", novelId);
        return agentService.listNarrativeEngineArtifacts(novelId);
    }

    /** M7：单章叙事引擎侧车；无数据时 `artifact` 为 null。 */
    @GetMapping("/{novelId}/chapters/{chapterNumber}/narrative-artifact")
    public Map<String, Object> getNarrativeArtifact(@PathVariable Long novelId, @PathVariable int chapterNumber) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 第 {} 章叙事引擎侧车", novelId, chapterNumber);
        return agentService.getNarrativeEngineArtifactForChapter(novelId, chapterNumber);
    }

    /** M9：全书跨章叙事状态快照（聚合承接、侧车、事实、阶段快照）；无数据时 `narrativeState` 为 null。 */
    @GetMapping("/{novelId}/narrative-state")
    public Map<String, Object> getNarrativeState(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的跨章叙事状态 M9", novelId);
        return agentService.getNarrativeStateForNovel(novelId);
    }

    /** 书本级角色动态状态（叙事调度闭环）；无数据时返回空数组。 */
    @GetMapping("/{novelId}/character-narrative-states")
    public List<NovelCharacterState> listCharacterNarrativeStates(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的角色动态状态列表", novelId);
        return characterNarrativeStateService.listStates(novelId);
    }

    @GetMapping("/{novelId}/plot-snapshots")
    public Object getPlotSnapshots(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的阶段快照", novelId);
        return plotSnapshotRepository.findByNovelIdOrderBySnapshotChapterDesc(novelId);
    }

    @GetMapping("/{novelId}/regeneration-tasks")
    public Map<String, Object> getRegenerationTasks(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的区间重生任务状态", novelId);
        List<String> runningRanges = regenerationTaskGuardService.getRunningRanges(novelId);
        // 任务化后：优先展示 DB 中的活跃任务区间（跨实例）
        try {
            List<GenerationTask> active = generationTaskService.listTasksByNovel(novelId).stream()
                    .filter(t -> ACTIVE_TASK_STATUSES.contains(t.getStatus()))
                    .filter(t -> t.getRangeFrom() != null && t.getRangeTo() != null)
                    .toList();
            if (!active.isEmpty()) {
                runningRanges = active.stream().map(t -> t.getRangeFrom() + "-" + t.getRangeTo()).distinct().toList();
            }
        } catch (Exception ignore) {
        }
        Novel novel = novelRepository.findById(novelId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("hasRunningTask", !runningRanges.isEmpty());
        result.put("runningRanges", runningRanges);
        if (novel != null) {
            result.put("persistedWorkbench", buildPersistedWorkbench(novel));
        }
        return result;
    }

    @GetMapping("/{novelId}/generation-tasks")
    public Map<String, Object> getGenerationTasks(@PathVariable Long novelId) {
        guardRead(novelId);
        List<GenerationTask> tasks = generationTaskService.listTasksByNovel(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("novelId", novelId);
        result.put("tasks", tasks);
        return result;
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getGenerationTask(@PathVariable Long taskId) {
        guardTask(taskId);
        return generationTaskService.getTask(taskId)
                .map(task -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("code", "OK");
                    result.put("task", task);
                    return result;
                })
                .orElseGet(() -> error("TASK_NOT_FOUND", "任务不存在: " + taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelGenerationTask(@PathVariable Long taskId) {
        guardTask(taskId);
        boolean ok = generationTaskService.cancelTask(taskId);
        if (!ok) return error("TASK_CANCEL_FAILED", "任务不存在或已完成，无法取消");
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return success("任务已取消", data);
    }

    @PostMapping("/tasks/{taskId}/kick")
    public Map<String, Object> kickPendingTask(@PathVariable Long taskId) {
        guardTask(taskId);
        boolean ok = generationTaskService.kickIfPending(taskId);
        if (!ok) return error("TASK_KICK_FAILED", "仅 PENDING 任务可手动触发执行，或任务不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return success("任务已触发执行", data);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Map<String, Object> retryGenerationTask(@PathVariable Long taskId) {
        guardTask(taskId);
        return generationTaskService.retryTask(taskId)
                .map(task -> {
                    generationTaskService.executeAsync(task.getId());
                    Map<String, Object> data = new HashMap<>();
                    data.put("taskId", task.getId());
                    data.put("status", task.getStatus());
                    return success("任务已重新入队并恢复执行", data);
                })
                .orElseGet(() -> error("TASK_RETRY_FAILED", "仅 FAILED/CANCELLED 任务可重试，或任务不存在"));
    }

    /** 前端监控：聚合 DB 工作台 + 内存区间锁 + 非 READY 章节列表。 */
    @GetMapping("/{novelId}/writing-monitor")
    public Map<String, Object> getWritingMonitor(@PathVariable Long novelId) {
        guardRead(novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("novelId", novelId);
        result.putAll(buildPersistedWorkbench(novel));
        result.put("volatileGuardRanges", regenerationTaskGuardService.getRunningRanges(novelId));
        result.put("hasVolatileGuardOverlap", regenerationTaskGuardService.hasRunningTask(novelId));

        List<Map<String, Object>> chapterBusyViews = new java.util.ArrayList<>();
        for (Chapter c : agentService.getChaptersByNovelId(novelId)) {
            String st = c.getWriteState() == null ? ChapterWriteState.READY.name() : c.getWriteState();
            if (!ChapterWriteState.READY.name().equals(st)) {
                Map<String, Object> row = new HashMap<>();
                row.put("chapterNumber", c.getChapterNumber());
                row.put("title", c.getTitle());
                row.put("writeState", st);
                row.put("writeStateUpdatedAt", c.getWriteStateUpdatedAt());
                chapterBusyViews.add(row);
            }
        }
        result.put("chaptersWithActiveWriteMarks", chapterBusyViews);
        return result;
    }

    @GetMapping("/{novelId}/progress")
    public Map<String, Object> getProgress(@PathVariable Long novelId) {
        guardRead(novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> logs = generationLogRepository.findByNovelId(novelId);
        boolean outlineReady = novel.getDescription() != null && !novel.getDescription().isBlank() && !"AI generated novel".equals(novel.getDescription()) && !"AI生成的爽文小说".equals(novel.getDescription());
        long failedCount = logs.stream().filter(log -> "failed".equals(log.getStatus())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("novelId", novelId);
        result.put("title", novel.getTitle());
        result.put("outlineReady", outlineReady);
        result.put("charactersReady", !profiles.isEmpty());
        result.put("chapterCount", chapters.size());
        result.put("generationLogCount", logs.size());
        result.put("failedCount", failedCount);
        result.put("status", "success");
        result.put("code", "OK");
        result.put("generationStatus", failedCount > 0 ? "failed" : (outlineReady && !profiles.isEmpty() ? "generating_or_ready" : "generating"));
        result.put("lastUpdateTime", novel.getUpdateTime());
        result.putAll(buildPersistedWorkbench(novel));
        return result;
    }

    /** 可与 {@link NovelWritePhase} / {@link ChapterWriteState} 配套展示的核心字段快照。 */
    private Map<String, Object> buildPersistedWorkbench(Novel novel) {
        Map<String, Object> map = new HashMap<>();
        if (novel == null) return map;
        // 1) 先取 DB 任务（最可信，可跨实例），用于展示“全区间 + 当前进度”
        try {
            List<GenerationTask> active = generationTaskService.listTasksByNovel(novel.getId()).stream()
                    .filter(t -> ACTIVE_TASK_STATUSES.contains(t.getStatus()))
                    .filter(t -> t.getRangeFrom() != null && t.getRangeTo() != null)
                    .toList();
            if (!active.isEmpty()) {
                // 优先 RUNNING，其次 PENDING
                GenerationTask picked = active.stream().filter(t -> "RUNNING".equals(t.getStatus())).findFirst().orElse(active.get(0));
                int from = Math.min(picked.getRangeFrom(), picked.getRangeTo());
                int to = Math.max(picked.getRangeFrom(), picked.getRangeTo());
                Integer cur = picked.getCurrentChapter();
                int cursor = cur == null ? from : Math.min(to, Math.max(from, cur + 1));
                map.put("writePhase", mapTaskTypeToPhase(picked.getTaskType()));
                map.put("writeRangeFrom", from);
                map.put("writeRangeTo", to);
                map.put("writeCursorChapter", cursor);
                map.put("writePhaseDetail", picked.getTaskType() + " " + from + "-" + to);
                map.put("writeStartedAt", picked.getStartedAt());
                map.put("writeUpdatedAt", picked.getHeartbeatAt());
                map.put("isBusyPersisted", true);
                map.put("activeTaskId", picked.getId());
                map.put("activeTaskStatus", picked.getStatus());
                map.put("activeTaskCurrentChapter", picked.getCurrentChapter());
                return map;
            }
        } catch (Exception ignore) {
        }

        // 2) 兜底：旧版工作台字段（单章流程仍会写）
        String phase = novel.getWritePhase() == null || novel.getWritePhase().isBlank()
                ? NovelWritePhase.IDLE.name() : novel.getWritePhase();
        boolean busyPersisted = !NovelWritePhase.IDLE.name().equals(phase);
        map.put("writePhase", phase);
        map.put("writeRangeFrom", novel.getWriteRangeFrom());
        map.put("writeRangeTo", novel.getWriteRangeTo());
        map.put("writeCursorChapter", novel.getWriteCursorChapter());
        map.put("writePhaseDetail", novel.getWritePhaseDetail());
        map.put("writeStartedAt", novel.getWriteStartedAt());
        map.put("writeUpdatedAt", novel.getWriteUpdatedAt());
        map.put("isBusyPersisted", busyPersisted);
        return map;
    }

    private String mapTaskTypeToPhase(String taskType) {
        if (taskType == null) return NovelWritePhase.IDLE.name();
        return switch (taskType) {
            case "REGENERATE_RANGE" -> NovelWritePhase.REGENERATING_RANGE.name();
            case "AUTO_CONTINUE_RANGE" -> NovelWritePhase.AUTO_CONTINUE_RANGE.name();
            case "CONTINUE_SINGLE" -> NovelWritePhase.SINGLE_CONTINUE.name();
            default -> NovelWritePhase.IDLE.name();
        };
    }

    @GetMapping("/{novelId}/creative-process")
    public Map<String, Object> getCreativeProcess(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】获取小说 {} 的创作过程详情", novelId);
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
        List<CharacterProfile> profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        List<GenerationLog> logs = generationLogRepository.findByNovelId(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("novel", novel);
        result.put("outline", novel.getDescription());
        result.put("globalSetting", novel.getGenerationSetting());
        result.put("characterProfiles", profiles);
        result.put("chapters", chapters);
        result.put("generationLogs", logs);
        result.put("timeline", buildCreativeTimeline(novel, profiles, chapters, logs));
        return result;
    }

    private List<Map<String, Object>> buildCreativeTimeline(Novel novel, List<CharacterProfile> profiles, List<Chapter> chapters, List<GenerationLog> logs) {
        List<Map<String, Object>> timeline = new java.util.ArrayList<>();
        Map<String, Object> novelStep = new HashMap<>();
        novelStep.put("type", "novel_created"); novelStep.put("title", "创建小说"); novelStep.put("content", novel.getTopic()); novelStep.put("setting", novel.getGenerationSetting()); novelStep.put("serializationPlatform", novel.getSerializationPlatform()); novelStep.put("creatorNote", novel.getCreatorNote()); novelStep.put("createTime", novel.getCreateTime()); timeline.add(novelStep);
        Map<String, Object> outlineStep = new HashMap<>();
        outlineStep.put("type", "outline"); outlineStep.put("title", "生成故事大纲"); outlineStep.put("content", novel.getDescription()); outlineStep.put("logs", logs.stream().filter(log -> "outline".equals(log.getGenerationType())).toList()); timeline.add(outlineStep);
        Map<String, Object> profileStep = new HashMap<>();
        profileStep.put("type", "character_profile"); profileStep.put("title", "生成角色设定"); profileStep.put("content", profiles); profileStep.put("logs", logs.stream().filter(log -> "character_profile".equals(log.getGenerationType())).toList()); timeline.add(profileStep);
        for (Chapter chapter : chapters) {
            Map<String, Object> step = new HashMap<>();
            step.put("type", "chapter"); step.put("title", chapter.getTitle()); step.put("chapterNumber", chapter.getChapterNumber()); step.put("content", chapter.getContent()); step.put("setting", chapter.getGenerationSetting()); step.put("createTime", chapter.getCreateTime());
            step.put("logs", logs.stream().filter(log -> "chapter".equals(log.getGenerationType()) && chapter.getChapterNumber().equals(log.getChapterNumber())).toList());
            timeline.add(step);
        }
        return timeline;
    }

    @PostMapping("/create")
    public Map<String, Object> createNovel(@RequestBody(required = false) CreateRequest request) {
        log.info("【API请求】新建小说，题材: {}, 设定长度: {}", request == null ? "未提供" : request.getTopic(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) return error("INVALID_ARGUMENT", "题材不能为空");
            // 先落库小说记录，再用可恢复任务驱动首次生成（避免进程中断丢任务）
            boolean hotMeme = Boolean.TRUE.equals(request.getHotMemeEnabled());
            String styleJson = null;
            if (request.getWritingStyleParams() != null && !request.getWritingStyleParams().isEmpty()) {
                styleJson = objectMapper.writeValueAsString(request.getWritingStyleParams());
            }
            Long userId = novelLibraryAccessService.getCurrentUserId();
            Novel novel = agentService.createNovel(userId == null ? 0L : userId, request.getTopic(), request.getGenerationSetting(), WritingPipeline.POWER_FANTASY, hotMeme, styleJson,
                    request.getSerializationPlatform(), request.getCreatorNote(),
                    request.getOutlineDetailedPrefixChapters(), request.getOutlineMinRoadmapChapters());
            GenerationTask task = generationTaskService.enqueueInitialBootstrapTask(novel.getId(), 5);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("novelId", novel.getId());
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            return success("创作任务已入队并启动（可恢复），请通过任务接口轮询查看", data);
        } catch (Exception e) {
            log.warn("新建小说任务失败", e);
            return error("CREATE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{novelId}/continue")
    public Map<String, Object> continueNovel(@PathVariable Long novelId, @RequestBody(required = false) ContinueRequest request) {
        guardRead(novelId);
        log.info("【API请求】续写小说 {}, 目标章节: {}, 设定长度: {}", novelId, request == null ? "自动下一章" : request.getChapterNumber(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
            Integer chapterNumber = request == null ? null : request.getChapterNumber();
            int targetChapter = chapterNumber == null ? chapters.size() + 1 : chapterNumber;
            if (regenerationTaskGuardService.hasOverlap(novelId, targetChapter, targetChapter)) {
                return error("TASK_CONFLICT", "目标章节正在重生/续写中，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            GenerationTask task = generationTaskService.enqueueContinueTask(novelId, chapterNumber, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("targetChapter", task.getRangeFrom());
            return success("续写任务已入队并启动，可通过 generation-tasks 查看恢复进度", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
            log.warn("续写任务入队失败 novelId={}", novelId, e);
            return error("CONTINUE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{novelId}/auto-continue")
    public Map<String, Object> autoContinueNovel(@PathVariable Long novelId, @RequestBody(required = false) AutoContinueRequest request) {
        guardRead(novelId);
        log.info("【API请求】自动续写小说 {}, 目标章节数: {}, 设定长度: {}", novelId, request == null ? "默认" : request.getTargetChapterCount(), textLength(request == null ? null : request.getGenerationSetting()));
        try {
            List<Chapter> chapters = agentService.getChaptersByNovelId(novelId);
            int current = chapters.size();
            Integer targetChapterCount = request == null ? null : request.getTargetChapterCount();
            int target = targetChapterCount == null ? current + 1 : targetChapterCount;
            if (target > current && regenerationTaskGuardService.hasOverlap(novelId, current + 1, target)) {
                return error("TASK_CONFLICT", "目标区间存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            GenerationTask task = generationTaskService.enqueueAutoContinueTask(novelId, targetChapterCount, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("rangeFrom", task.getRangeFrom());
            data.put("rangeTo", task.getRangeTo());
            return success("自动续写任务已入队并启动，可在重启后继续", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
            log.warn("自动续写任务入队失败 novelId={}", novelId, e);
            return error("AUTO_CONTINUE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{novelId}/chapters/{chapterNumber}/regenerate")
    public Map<String, Object> regenerateChapter(@PathVariable Long novelId, @PathVariable Integer chapterNumber, @RequestBody(required = false) RegenerateRequest request) {
        guardRead(novelId);
        log.info("【API请求】重新生成小说 {} 的第{}章，设定长度: {}", novelId, chapterNumber, textLength(request == null ? null : request.getGenerationSetting()));
        try {
            if (chapterNumber <= 0) { return error("INVALID_ARGUMENT", "章节号必须大于0"); }
            if (regenerationTaskGuardService.hasOverlap(novelId, chapterNumber, chapterNumber)) {
                return error("TASK_CONFLICT", "该章节已存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            GenerationTask task = generationTaskService.enqueueRegenerateRangeTask(novelId, chapterNumber, chapterNumber, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            data.put("targetChapter", chapterNumber);
            return success("重新生成任务已入队并启动，可在重启后恢复", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
            log.warn("重生章节任务入队失败 novelId={} chapter={}", novelId, chapterNumber, e);
            return error("REGENERATE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{novelId}/chapters/regenerate-range")
    public Map<String, Object> regenerateRange(@PathVariable Long novelId, @RequestBody RegenerateRangeRequest request) {
        guardRead(novelId);
        try {
            if (request == null || request.getStartChapter() == null || request.getEndChapter() == null) {
                return error("INVALID_ARGUMENT", "startChapter 和 endChapter 不能为空");
            }
            Integer startChapter = request.getStartChapter();
            Integer endChapter = request.getEndChapter();
            int from = Math.min(startChapter, endChapter);
            int to = Math.max(startChapter, endChapter);
            if (regenerationTaskGuardService.hasOverlap(novelId, from, to)) {
                return error("TASK_CONFLICT", "目标区间存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request.getGenerationSetting();
            GenerationTask task = generationTaskService.enqueueRegenerateRangeTask(novelId, startChapter, endChapter, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("startChapter", startChapter);
            data.put("endChapter", endChapter);
            data.put("taskId", task.getId());
            data.put("taskType", task.getTaskType());
            return success("区间重生任务已入队并启动，可在重启后恢复", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
            log.warn("区间重生任务入队失败 novelId={}", novelId, e);
            return error("REGENERATE_RANGE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @GetMapping("/{novelId}/export")
    public String exportNovel(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】导出小说 {}", novelId);
        return agentService.exportNovelToTxt(novelId);
    }

    @GetMapping("/{novelId}/export-health")
    public Map<String, Object> checkExportHealth(@PathVariable Long novelId) {
        guardRead(novelId);
        log.info("【API请求】导出前体检 {}", novelId);
        return novelExportService.checkExportHealth(novelId);
    }
    private int textLength(String text) { return text == null ? 0 : text.length(); }
    private Map<String, Object> success(String message, Map<String, Object> data) {
        return ApiResponse.success(message, data).toMap();
    }

    private Map<String, Object> error(String code, String message) {
        return ApiResponse.error(code, message).toMap();
    }

    // ──── 新增 API (Phase 3.2) ────

    @GetMapping("/{novelId}/contracts")
    public Map<String, Object> getContractTree(@PathVariable Long novelId) {
        guardRead(novelId);
        return storyContractService.getContractTree(novelId);
    }

    @GetMapping("/{novelId}/strand-stats")
    public Map<String, Object> strandStats(@PathVariable Long novelId) {
        guardRead(novelId);
        var stats = strandWeaveService.getStrandStats(novelId);
        Map<String, Object> result = new HashMap<>();
        result.put("questCount", stats.questCount());
        result.put("fireCount", stats.fireCount());
        result.put("constellationCount", stats.constellationCount());
        result.put("lastQuestChapter", stats.lastQuestChapter());
        result.put("lastFireChapter", stats.lastFireChapter());
        result.put("lastConstellationChapter", stats.lastConstellationChapter());
        result.put("currentDominant", stats.currentDominant() != null ? stats.currentDominant().name() : null);
        result.put("chaptersSinceSwitch", stats.chaptersSinceSwitch());
        result.put("suggestion", stats.suggestion());
        result.put("warning", stats.warning());
        return result;
    }

    @GetMapping("/{novelId}/foreshadowing")
    public List<com.start.agent.model.Foreshadowing> listForeshadowing(@PathVariable Long novelId) {
        guardRead(novelId);
        return foreshadowingRepository.findByNovelIdOrderByPlantedChapterAsc(novelId);
    }

    @GetMapping("/{novelId}/foreshadowing/gantt")
    public List<Map<String, Object>> foreshadowingGantt(@PathVariable Long novelId) {
        guardRead(novelId);
        return foreshadowingService.getGanttData(novelId);
    }

    @PostMapping("/{novelId}/foreshadowing")
    public Map<String, Object> addForeshadowing(@PathVariable Long novelId, @RequestBody Map<String, Object> body) {
        guardRead(novelId);
        String content = (String) body.getOrDefault("content", "");
        String loopType = (String) body.getOrDefault("loopType", "MYSTERY");
        String urgency = (String) body.getOrDefault("urgency", "low");
        Integer plantedChapter = body.get("plantedChapter") instanceof Number n ? n.intValue() : 1;
        Integer deadlineChapter = body.get("deadlineChapter") instanceof Number n ? n.intValue() : null;
        var f = foreshadowingService.plantLoop(novelId, plantedChapter, content, loopType, urgency, deadlineChapter);
        Map<String, Object> result = new HashMap<>();
        result.put("id", f.getId());
        result.put("status", "success");
        return result;
    }

    @PutMapping("/{novelId}/foreshadowing/{fid}")
    public Map<String, Object> updateForeshadowing(@PathVariable Long novelId, @PathVariable Long fid,
                                                    @RequestBody Map<String, Object> body) {
        guardRead(novelId);
        String action = (String) body.getOrDefault("action", "remind");
        Integer chapterNumber = body.get("chapterNumber") instanceof Number n ? n.intValue() : null;
        if ("payoff".equals(action) && chapterNumber != null) {
            foreshadowingService.payoffLoop(fid, chapterNumber);
        } else if (chapterNumber != null) {
            foreshadowingService.remindLoop(fid, chapterNumber);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        return result;
    }

    @GetMapping("/{novelId}/reading-power")
    public List<Map<String, Object>> readingPowerMetrics(@PathVariable Long novelId) {
        guardRead(novelId);
        return readingPowerService.getChapterMetrics(novelId);
    }

    @GetMapping("/{novelId}/reading-power/trend")
    public List<Map<String, Object>> readingPowerTrend(@PathVariable Long novelId) {
        guardRead(novelId);
        return readingPowerService.getChapterMetrics(novelId);
    }

    @GetMapping("/{novelId}/chapters/{n}/ai-flavor-report")
    public Map<String, Object> aiFlavorReport(@PathVariable Long novelId, @PathVariable Integer n) {
        guardRead(novelId);
        var chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, n).orElse(null);
        if (chapter == null || chapter.getContent() == null) {
            return Map.of("issues", List.of(), "summary", "章节不存在或内容为空", "passed", true);
        }
        return aiFlavorDetector.buildReport(chapter.getContent());
    }

    @GetMapping("/{novelId}/commit-history")
    public List<Map<String, Object>> commitHistory(@PathVariable Long novelId) {
        guardRead(novelId);
        return chapterCommitService.getCommitHistory(novelId);
    }

    @GetMapping("/knowledge/search")
    public List<Map<String, Object>> searchKnowledge(@RequestParam(required = false) String table,
                                                      @RequestParam String query) {
        return writingKnowledgeService.search(table, query);
    }

    @GetMapping("/knowledge/tables")
    public List<String> knowledgeTables() {
        return List.of("题材与调性推理", "裁决规则", "人设与关系", "写作技法", "命名规则", "场景写法", "桥段套路", "爽点与节奏", "金手指与设定");
    }

    @Data static class CreateRequest {
        private String topic;
        private String generationSetting;
        /** 是否开启「少量网络热梗」模式；默认 false。 */
        private Boolean hotMemeEnabled;
        /**
         * 可选文风微参，如 styleIntensity、dialogueRatioHint、humorLevel、periodStrictness（英文枚举值）。
         */
        private Map<String, Object> writingStyleParams;
        /** 连载平台（展示用，可选）。 */
        private String serializationPlatform;
        /** 创作说明：本书用途、备注（可选，不参与 AI）。 */
        private String creatorNote;
        /**
         * 大纲「开篇逐章细纲」最少章数（可选）；不传则用服务端 {@code novel.outline.detailed-prefix-chapters}。
         * 允许范围约 15～150，服务端会 clamp。
         */
        private Integer outlineDetailedPrefixChapters;
        /**
         * 大纲全书路线图覆盖的末章号下限（可选）；不传则用 {@code novel.outline.min-roadmap-chapters}。
         * 允许范围约「≥细纲+15」～600，服务端会 clamp。
         */
        private Integer outlineMinRoadmapChapters;
    }

    @Data static class RegenerateOutlineRequest {
        /**
         * 作者/编辑对本次大纲的方向建议（节奏、禁忌、必须保留的剧情点、希望延后揭晓的伏笔等）；可为空表示仅按题材与设定重生成。
         */
        private String hint;
        /** 可选：覆盖本书存稿的大纲规划参数（仅当次生成生效逻辑与创建时一致，非空则写入实体）。 */
        private Integer outlineDetailedPrefixChapters;
        private Integer outlineMinRoadmapChapters;
    }

    @Data static class DeleteNovelRequest {
        /** 须与 GET delete-guard 返回的书名完全一致（trim 后比对）。 */
        private String confirmTitle;
        /** 须原样等于 {@link NovelDeletionService#REQUIRED_PHRASE}。 */
        private String typedPhrase;
        /** 须为 true，表示知晓不可恢复。 */
        private Boolean acknowledgeIrreversible;
    }

    @Data static class BookMetaRequest {
        private String serializationPlatform;
        private String creatorNote;
    }

    @Data static class WritingStyleParamsRequest {
        private Map<String, Object> writingStyleParams;
        /** 可选：直接传 JSON 字符串（与 writingStyleParams 二选一）。 */
        private String writingStyleParamsRaw;
    }

    @Data static class HotMemeRequest {
        private Boolean enabled;
    }
    @Data static class UpdateContentRequest { private String content; private String title; private String editNote; }
    @Data static class ContinueRequest { private Integer chapterNumber; private String generationSetting; }
    @Data static class AutoContinueRequest { private Integer targetChapterCount; private String generationSetting; }
    @Data static class RegenerateRequest { private String generationSetting; }
    @Data static class RegenerateRangeRequest { private Integer startChapter; private Integer endChapter; private String generationSetting; }
    @Data static class PipelineRequest { private String pipeline; }
    @Data static class LibraryVisibilityRequest { private Boolean libraryPublic; }
    @Data static class RepairCharacterRequest {
        private Boolean forceRegenerate;
        private String rebuildMode;
        private String characterContextHint;
        // backward compatibility
        private String firstSwordMaster;
        private List<String> targetCharacterNames;
        private String extraHint;
    }
}
