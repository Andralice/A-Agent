package com.start.agent.exception;

/** 资源不存在；全局处理器返回 404 + 简短中文，不把内部 ID 细节暴露给前端（可选传友好文案）。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String userMessage) {
        super(userMessage);
    }

    public static NotFoundException novel() {
        return new NotFoundException("未找到该小说，请检查链接或刷新列表");
    }
}
