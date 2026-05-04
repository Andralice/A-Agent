package com.start.agent.controller;

import com.start.agent.model.WritingPipeline;
import com.start.agent.model.GenerationTask;
import com.start.agent.service.NovelAgentService;
import com.start.agent.service.NovelLibraryAccessService;
import com.start.agent.service.GenerationTaskService;
import com.start.agent.service.RegenerationTaskGuardService;
import com.start.agent.exception.UserFriendlyExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP API：按「文风 URL 段」创建小说并续写，内部映射为 {@link WritingPipeline}。
 */
@Slf4j
@RestController
@RequestMapping("/api/novel-style")
@CrossOrigin(origins = "*")
public class NovelStyleController {
    private final NovelAgentService agentService;
    private final RegenerationTaskGuardService regenerationTaskGuardService;
    private final GenerationTaskService generationTaskService;
    private final NovelLibraryAccessService novelLibraryAccessService;
    private final ObjectMapper objectMapper;

    public NovelStyleController(NovelAgentService agentService,
                                RegenerationTaskGuardService regenerationTaskGuardService,
                                GenerationTaskService generationTaskService,
                                NovelLibraryAccessService novelLibraryAccessService,
                                ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.regenerationTaskGuardService = regenerationTaskGuardService;
        this.generationTaskService = generationTaskService;
        this.novelLibraryAccessService = novelLibraryAccessService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{style}/create")
    public Map<String, Object> createByStyle(@PathVariable String style, @RequestBody(required = false) CreateRequest request) {
        try {
            if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) {
                return error("INVALID_ARGUMENT", "题材不能为空");
            }
            WritingPipeline pipeline = WritingPipeline.fromPath(style);
            boolean hotMeme = Boolean.TRUE.equals(request.getHotMemeEnabled());
            String styleJson = null;
            if (request.getWritingStyleParams() != null && !request.getWritingStyleParams().isEmpty()) {
                styleJson = objectMapper.writeValueAsString(request.getWritingStyleParams());
            }
            String finalStyleJson = styleJson;
            CompletableFuture.runAsync(() -> agentService.processAndSend(0L, request.getTopic(), request.getGenerationSetting(), pipeline, hotMeme, finalStyleJson,
                    request.getSerializationPlatform(), request.getCreatorNote(),
                    request.getOutlineDetailedPrefixChapters(), request.getOutlineMinRoadmapChapters()));
            Map<String, Object> data = new HashMap<>();
            data.put("pipeline", pipeline.name());
            data.put("hotMemeEnabled", hotMeme);
            return success("风格化创作任务已启动", data);
        } catch (Exception e) {
            return error("STYLE_CREATE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    @PostMapping("/{style}/{novelId}/continue")
    public Map<String, Object> continueByStyle(@PathVariable String style, @PathVariable Long novelId,
                                               @RequestBody(required = false) ContinueRequest request) {
        try {
            novelLibraryAccessService.assertCanRead(novelId);
            Integer chapterNumber = request == null ? null : request.getChapterNumber();
            int targetChapter = chapterNumber == null ? agentService.getChaptersByNovelId(novelId).size() + 1 : chapterNumber;
            if (regenerationTaskGuardService.hasOverlap(novelId, targetChapter, targetChapter)) {
                return error("TASK_CONFLICT", "该章节已存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            GenerationTask task = generationTaskService.enqueueContinueTask(novelId, chapterNumber, generationSetting);
            generationTaskService.executeAsync(task.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("pipelineHint", WritingPipeline.fromPath(style).name());
            data.put("taskId", task.getId());
            return success("风格化续写任务已启动", data);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) return error("TASK_CONFLICT", UserFriendlyExceptions.mask(e));
            return error("STYLE_CONTINUE_TASK_FAILED", UserFriendlyExceptions.mask(e));
        }
    }

    private Map<String, Object> success(String message, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("message", message);
        if (data != null) result.putAll(data);
        return result;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    @Data
    static class CreateRequest {
        private String topic;
        private String generationSetting;
        private Boolean hotMemeEnabled;
        private Map<String, Object> writingStyleParams;
        private String serializationPlatform;
        private String creatorNote;
        private Integer outlineDetailedPrefixChapters;
        private Integer outlineMinRoadmapChapters;
    }

    @Data
    static class ContinueRequest {
        private Integer chapterNumber;
        private String generationSetting;
    }
}
