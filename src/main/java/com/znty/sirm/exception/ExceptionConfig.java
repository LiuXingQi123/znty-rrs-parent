package com.znty.sirm.exception;

import com.znty.sirm.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class ExceptionConfig {

    /** 系统异常需记录日志，业务异常不记录 */
    private static final Logger log = LoggerFactory.getLogger(ExceptionConfig.class);

    /** 处理业务异常。 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBiz(BizException e) {
        // code 仅用于日志区分（400/404 等），不再进入响应体
        log.warn("业务异常 code={}: {}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    /** 处理系统异常。 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail("系统繁忙，请稍后重试");
    }
}
