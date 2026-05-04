package com.start.agent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 大纲规划默认值（单独控制器，避免与 {@code /api/novel/{novelId}} 等映射在部分环境下的解析顺序问题）。
 */
@RestController
@RequestMapping("/api/novel/config")
@CrossOrigin(origins = "*")
public class NovelOutlineConfigController {

    @Value("${app.security.enabled:false}")
    private boolean appSecurityEnabled;

    @Value("${app.security.jwt-expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${novel.auto-continue.default-target:20}")
    private int autoContinueDefaultTarget;

    @Value("${novel.auto-continue.max-target:200}")
    private int autoContinueMaxTarget;

    @Value("${novel.narrative-engine.enabled:true}")
    private boolean narrativeEngineEnabled;

    @Value("${novel.narrative-engine.m7-artifact-enabled:true}")
    private boolean m7ArtifactEnabled;

    @Value("${novel.narrative-engine.m9-crosscut-enabled:false}")
    private boolean m9CrosscutEnabled;

    @Value("${novel.outline.detailed-prefix-chapters:40}")
    private int outlinePlanDetailedDefault;
    @Value("${novel.outline.min-roadmap-chapters:120}")
    private int outlinePlanRoadmapDefault;

    @Value("${novel.outline.two-phase-graph-enabled:true}")
    private boolean outlineTwoPhaseGraphEnabled;

    @Value("${novel.outline.graph-phase-max-tokens:3600}")
    private int outlineGraphPhaseMaxTokens;

    /**
     * 联调用：返回与 UI 校验相关的服务端配置快照（无密钥）。与 web-agent「前端对接需求」§4.6 对齐。
     */
    @GetMapping("/frontend-runtime")
    public Map<String, Object> frontendRuntime() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("message", "前端联调用运行时配置（无敏感信息）");
        result.put("securityEnabled", appSecurityEnabled);
        result.put("jwtExpirationMs", jwtExpirationMs);
        result.put("autoContinueDefaultTarget", autoContinueDefaultTarget);
        result.put("autoContinueMaxTarget", autoContinueMaxTarget);
        result.put("outlineDetailedPrefixChaptersDefault", outlinePlanDetailedDefault);
        result.put("outlineMinRoadmapChaptersDefault", outlinePlanRoadmapDefault);
        result.put("outlineDetailedPrefixChaptersMin", 15);
        result.put("outlineDetailedPrefixChaptersMax", 150);
        result.put("outlineMinRoadmapChaptersMin", 30);
        result.put("outlineMinRoadmapChaptersMax", 600);
        result.put("narrativeEngineEnabled", narrativeEngineEnabled);
        result.put("m7ArtifactEnabled", m7ArtifactEnabled);
        result.put("m9CrosscutEnabled", m9CrosscutEnabled);
        result.put("outlineTwoPhaseGraphEnabled", outlineTwoPhaseGraphEnabled);
        result.put("outlineGraphPhaseMaxTokens", outlineGraphPhaseMaxTokens);
        return result;
    }

    @GetMapping("/outline-plan-defaults")
    public Map<String, Object> outlinePlanDefaults() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("code", "OK");
        result.put("message", "大纲规划参数默认值与允许范围（创建时可覆盖）");
        result.put("detailedPrefixChapters", outlinePlanDetailedDefault);
        result.put("minRoadmapChapters", outlinePlanRoadmapDefault);
        result.put("detailedPrefixChaptersMin", 15);
        result.put("detailedPrefixChaptersMax", 150);
        result.put("minRoadmapChaptersMin", 30);
        result.put("minRoadmapChaptersMax", 600);
        return result;
    }
}
