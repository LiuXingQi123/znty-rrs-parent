package com.znty.sirm.common;

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
    private int code;    // 业务状态码：0 表示成功，非 0 表示各类错误
    private String message; // 响应描述信息，成功时为 "success"，失败时为错误说明
    private T data;         // 业务数据载体，失败时为 null

    /** 返回带数据的成功响应。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /** 返回无数据的成功响应。 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "success", null);
    }

    /** 返回失败响应。 */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
