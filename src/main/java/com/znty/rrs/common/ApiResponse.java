package com.znty.rrs.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装，所有接口均返回此结构，便于前端统一处理成功与失败场景。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /** 是否成功：true 表示成功，false 表示失败 */
    private boolean success;
    /** 响应描述信息，成功或失败时的说明文案 */
    private String message;
    /** 业务数据载体，失败时通常为 null */
    private T data;

    /** 返回带数据的成功响应，message 默认 "success"。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    /** 返回带数据的成功响应，并自定义 message。 */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    /** 返回无数据的成功响应。 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "success", null);
    }

    /** 返回失败响应。 */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }

    /** 返回带数据的失败响应。 */
    public static <T> ApiResponse<T> fail(String message, T data) {
        return new ApiResponse<>(false, message, data);
    }
}
