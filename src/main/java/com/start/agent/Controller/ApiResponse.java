package com.start.agent.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 统一 API 响应包装：消除 Controller 中 {@code Map<String,Object>} 与裸实体返回混用的问题。
 * JSON 结构保持为 {@code {status, code, message, data}} 与旧版兼容。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private String status;
    private String code;
    private String message;
    private Object data;

    private ApiResponse(String status, String code, String message, Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse success(String message, Object data) {
        return new ApiResponse("success", "OK", message, data);
    }

    public static ApiResponse success(String message) {
        return new ApiResponse("success", "OK", message, null);
    }

    public static ApiResponse error(String code, String message) {
        return new ApiResponse("error", code, message, null);
    }

    /** 将 ApiResponse 展平为 Map（兼容旧版 {@code Map<String,Object>} 调用方）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("status", status);
        map.put("code", code);
        map.put("message", message);
        if (data instanceof Map) {
            map.putAll((Map<String, Object>) data);
        } else if (data != null) {
            map.put("data", data);
        }
        return map;
    }

    // ── getters for Jackson ──

    public String getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}
