package com.start.agent.exception;

import com.start.agent.dto.ApiResponse;
import com.start.agent.service.NovelLibraryAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：面向前端只返回简短中文；详细堆栈仅写日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNotFound(NotFoundException e) {
        log.debug("资源不存在: {}", e.getMessage());
        return ApiResponse.error(404, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        String msg = UserFriendlyExceptions.mask(e);
        return ApiResponse.error(400, msg);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleIllegalState(IllegalStateException e) {
        log.warn("状态冲突: {}", e.getMessage());
        String msg = UserFriendlyExceptions.mask(e);
        return ApiResponse.error(409, msg);
    }

    @ExceptionHandler(NovelLibraryAccessService.ForbiddenLibraryOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleLibraryForbidden(NovelLibraryAccessService.ForbiddenLibraryOperationException e) {
        log.warn("书库权限: {}", e.getMessage());
        return ApiResponse.error(403, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(500, UserFriendlyExceptions.SERVER_BUSY);
    }
}

