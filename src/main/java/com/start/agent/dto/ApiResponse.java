package com.start.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 REST 响应壳：code / message / data，配合 {@link com.start.agent.exception.GlobalExceptionHandler} 使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    /** HTTP 语义码：如 400 参数错误、404 未找到、500 通用失败（前端用来区分 toast 类型）。 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

