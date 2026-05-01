package com.start.agent.controller;

import com.start.agent.model.WritingPipeline;
import com.start.agent.service.NovelAgentService;
import com.start.agent.service.RegenerationTaskGuardService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/novel-style")
@CrossOrigin(origins = "*")
public class NovelStyleController {
    private final NovelAgentService agentService;
    private final RegenerationTaskGuardService regenerationTaskGuardService;

    public NovelStyleController(NovelAgentService agentService, RegenerationTaskGuardService regenerationTaskGuardService) {
        this.agentService = agentService;
        this.regenerationTaskGuardService = regenerationTaskGuardService;
    }

    @PostMapping("/{style}/create")
    public Map<String, Object> createByStyle(@PathVariable String style, @RequestBody(required = false) CreateRequest request) {
        try {
            if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) {
                return error("INVALID_ARGUMENT", "题材不能为空");
            }
            WritingPipeline pipeline = WritingPipeline.fromPath(style);
            CompletableFuture.runAsync(() -> agentService.processAndSend(0L, request.getTopic(), request.getGenerationSetting(), pipeline));
            Map<String, Object> data = new HashMap<>();
            data.put("pipeline", pipeline.name());
            return success("风格化创作任务已启动", data);
        } catch (Exception e) {
            return error("STYLE_CREATE_TASK_FAILED", e.getMessage());
        }
    }

    @PostMapping("/{style}/{novelId}/continue")
    public Map<String, Object> continueByStyle(@PathVariable String style, @PathVariable Long novelId,
                                               @RequestBody(required = false) ContinueRequest request) {
        try {
            Integer chapterNumber = request == null ? null : request.getChapterNumber();
            int targetChapter = chapterNumber == null ? agentService.getChaptersByNovelId(novelId).size() + 1 : chapterNumber;
            if (regenerationTaskGuardService.hasOverlap(novelId, targetChapter, targetChapter)) {
                return error("TASK_CONFLICT", "该章节已存在进行中的重生/续写任务，请稍后重试。");
            }
            String generationSetting = request == null ? null : request.getGenerationSetting();
            CompletableFuture.runAsync(() -> agentService.continueChapter(0L, novelId.intValue(), chapterNumber, generationSetting));
            Map<String, Object> data = new HashMap<>();
            data.put("pipelineHint", WritingPipeline.fromPath(style).name());
            return success("风格化续写任务已启动", data);
        } catch (Exception e) {
            return error("STYLE_CONTINUE_TASK_FAILED", e.getMessage());
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
    }

    @Data
    static class ContinueRequest {
        private Integer chapterNumber;
        private String generationSetting;
    }
}
