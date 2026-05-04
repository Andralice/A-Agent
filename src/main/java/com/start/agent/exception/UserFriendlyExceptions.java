package com.start.agent.exception;

/**
 * 面向前端展示的简短中文提示；内部原因仅记日志，不把异常堆栈原文暴露给用户。
 */
public final class UserFriendlyExceptions {

    private UserFriendlyExceptions() {
    }

    public static final String GENERIC = "操作未能完成，请稍后重试";
    public static final String SERVER_BUSY = "服务暂时不可用，请稍后重试";
    public static final String NOT_FOUND = "请求的资源不存在";

    /** 控制器 catch 块：未知异常统一话术（detail 已记日志）。 */
    public static String mask(Throwable e) {
        if (e == null) {
            return GENERIC;
        }
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            String m = e.getMessage();
            if (m != null && !m.isBlank() && !looksLikeInternalLeak(m)) {
                return m;
            }
        }
        return GENERIC;
    }

    private static boolean looksLikeInternalLeak(String m) {
        String lower = m.toLowerCase();
        return lower.contains("exception")
                || lower.contains("sql")
                || lower.contains("nullpointer")
                || lower.contains("runtime")
                || lower.contains("timeout")
                || lower.contains("connection")
                || m.contains("	at ")
                || m.length() > 280;
    }
}
