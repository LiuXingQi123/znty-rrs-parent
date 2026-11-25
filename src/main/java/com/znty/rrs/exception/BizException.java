package com.znty.rrs.exception;

/**
 * 携带业务失败原因和错误码
 *
 * {@link ExceptionConfig#handleBiz(BizException)} 将业务文案转为接口失败响应，并把错误码记入服务端日志
 */
public class BizException extends RuntimeException {
    /** 仅用于服务端日志的业务错误码，未指定时为 400 */
    private final int code;

    /**
     * 使用指定的错误码和文案创建业务异常
     *
     * @param code 用于服务端日志的业务错误码
     * @param message 供调用方了解失败原因的业务文案
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用默认错误码 400 创建业务异常
     *
     * @param message 供调用方了解失败原因的业务文案
     */
    public BizException(String message) {
        this(400, message);
    }

    /**
     * 获取用于记录日志的业务错误码
     *
     * @return 创建异常时指定的错误码，未指定时返回 400
     */
    public int getCode() {
        return code;
    }
}
