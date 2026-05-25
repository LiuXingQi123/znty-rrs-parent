package com.znty.sirm.exception;

/**
 * 业务异常，由全局异常处理器转为 ApiResponse
 */
public class BizException extends RuntimeException {
    private final int code;

    /** 创建指定错误码的业务异常。 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 创建默认参数错误业务异常。 */
    public BizException(String message) {
        this(400, message);
    }

    /** 获取业务错误码。 */
    public int getCode() {
        return code;
    }
}
