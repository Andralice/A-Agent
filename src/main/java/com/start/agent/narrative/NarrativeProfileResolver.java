package com.start.agent.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.agent.model.WritingPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合并 {@link WritingPipeline} 默认叙事参数与小说 {@code writingStyleParams} 根节点下可选 {@code narrative} 对象。
 */
@Slf4j
@Component
public class NarrativeProfileResolver {

    private final ObjectMapper objectMapper;

    public NarrativeProfileResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param writingStyleParamsJson 小说实体上的 JSON 字符串，可为 null
     */
    public NarrativeProfile resolve(WritingPipeline pipeline, String writingStyleParamsJson) {
        NarrativeProfile base = NarrativeProfileDefaults.forPipeline(pipeline);
        if (writingStyleParamsJson == null || writingStyleParamsJson.isBlank()) {
            return base;
        }
        try {
            JsonNode root = objectMapper.readTree(writingStyleParamsJson.trim());
            JsonNode narrative = root.get("narrative");
            if (narrative == null || !narrative.isObject()) {
                return base;
            }
            return NarrativeProfile.applyOverrides(base, narrative);
        } catch (Exception e) {
            log.warn("narrative profile merge skipped: invalid writingStyleParams JSON: {}", e.getMessage());
            return base;
        }
    }

    /**
     * M6：叙事物理引擎模式；根字段 {@code narrativePhysicsMode} 可覆盖管线默认。
     */
    public NarrativePhysicsMode resolvePhysicsMode(WritingPipeline pipeline, String writingStyleParamsJson) {
        NarrativePhysicsMode fallback = NarrativePhysicsMode.fromPipeline(pipeline);
        if (writingStyleParamsJson == null || writingStyleParamsJson.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(writingStyleParamsJson.trim());
            if (!root.has("narrativePhysicsMode") || root.get("narrativePhysicsMode").isNull()) {
                return fallback;
            }
            return NarrativePhysicsMode.parseOverride(root.get("narrativePhysicsMode").asText(), fallback);
        } catch (Exception e) {
            log.warn("narrative physics mode parse skipped: {}", e.getMessage());
            return fallback;
        }
    }
}
