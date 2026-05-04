package com.start.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 全书可选「文风微参」，JSON 存于 {@link Novel#getWritingStyleParams()}。
 * 字段均可缺省；取值建议使用英文枚举串（大小写不敏感）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WritingStyleHints {

    /**
     * 叙事张扬度：mild（克制） / balanced（默认） / bold（更狠、更外放）。
     */
    private String styleIntensity;

    /**
     * 对白占比倾向：low / medium / high。
     */
    private String dialogueRatioHint;

    /**
     * 幽默与打趣程度：low / medium / high。
     */
    private String humorLevel;

    /**
     * 年代细节严苛度（年代文最有用）：loose / normal / strict。
     */
    private String periodStrictness;

    public boolean hasAny() {
        return nz(styleIntensity) || nz(dialogueRatioHint) || nz(humorLevel) || nz(periodStrictness);
    }

    private static boolean nz(String s) {
        return s != null && !s.isBlank();
    }

    /** 解析失败或空对象返回 null，调用方按「无微调」处理。 */
    public static WritingStyleHints parseNullable(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            WritingStyleHints h = objectMapper.readValue(json.trim(), WritingStyleHints.class);
            if (h == null || !h.hasAny()) {
                return null;
            }
            return h;
        } catch (Exception ignored) {
            return null;
        }
    }
}
