package com.start.agent.controller;

import com.start.agent.qq.QqMessageFacade;
import com.start.agent.service.CleanupService;
import com.start.agent.service.NovelAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/qq")
public class QQMessageController {
    private static final String RESPONSE_SUCCESS = "{\"status\":\"ok\",\"retcode\":0}";
    private static final String RESPONSE_ERROR = "{\"status\":\"failed\",\"retcode\":1,\"message\":\"处理失败\"}";
    private final NovelAgentService agentService;
    private final CleanupService cleanupService;
    private final QqMessageFacade qqMessageFacade;
    @Value("${app.admin-token:}") private String adminToken;

    public QQMessageController(NovelAgentService agentService, CleanupService cleanupService, QqMessageFacade qqMessageFacade) {
        this.agentService = agentService; this.cleanupService = cleanupService; this.qqMessageFacade = qqMessageFacade;
    }

    @PostMapping("/message")
    public String handleQQMessage(@RequestBody String payload) {
        try { log.debug("【收到请求】原始payload: {}", payload); qqMessageFacade.handle(payload); return RESPONSE_SUCCESS; }
        catch (Exception e) { log.error("【❌ 异常】处理QQ消息时发生错误", e); return RESPONSE_ERROR; }
    }

    @GetMapping("/export/{novelId}")
    public ResponseEntity<byte[]> exportNovel(@PathVariable Long novelId) {
        try {
            log.info("【📄 导出请求】收到导出请求 - 小说ID: {}", novelId);
            String txtContent = agentService.exportNovelToTxt(novelId);
            byte[] content = txtContent.getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", URLEncoder.encode("小说_" + novelId + ".txt", StandardCharsets.UTF_8));
            headers.setContentLength(content.length);
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (Exception e) {
            log.error("【❌ 导出失败】导出小说异常 - 小说ID: {}", novelId, e);
            return ResponseEntity.internalServerError().body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/cleanup")
    public String triggerCleanup(@RequestHeader(value = "X-Admin-Token", required = false) String requestToken) {
        if (!StringUtils.hasText(adminToken)) return "{\"status\":\"failed\",\"message\":\"管理接口未启用\"}";
        if (!adminToken.equals(requestToken)) return "{\"status\":\"failed\",\"message\":\"无权限\"}";
        try { return String.format("{\"status\":\"ok\",\"cleaned_count\":%d}", cleanupService.manualCleanupEmptyNovels()); }
        catch (Exception e) { log.error("【❌ 管理指令】清理任务触发失败", e); return "{\"status\":\"failed\",\"message\":\"清理任务执行失败\"}"; }
    }
}
