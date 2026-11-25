package com.znty.rrs.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应
 *
 * 接口使用成功标志、说明文案和业务数据返回结果，前端按成功标志处理成功或失败
 *
 * @param <T> 业务数据的类型，分页接口使用 {@link PageResult}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 接口处理结果，true 表示成功，false 表示失败 */
    private boolean success;

    /** 响应描述信息，成功或失败时的说明文案 */
    private String message;

    /** 返回给前端的业务数据，失败时通常为 null，调用方可按需附带数据 */
    private T data;

    /**
     * 使用默认文案创建带业务数据的成功响应
     *
     * @param <T> 业务数据的类型
     * @param data 返回给前端的业务数据
     * @return 文案为 {@code success} 且带有指定数据的成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    /**
     * 使用指定文案创建带业务数据的成功响应
     *
     * @param <T> 业务数据的类型
     * @param data 返回给前端的业务数据
     * @param message 自定义成功文案
     * @return 使用指定文案和数据的成功响应
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * 创建只有默认文案的成功响应
     *
     * @param <T> 响应声明的业务数据类型
     * @return 文案为 {@code success}、数据为 null 的成功响应
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "success", null);
    }

    /**
     * 使用指定文案创建失败响应
     *
     * @param <T> 响应声明的业务数据类型
     * @param message 供前端展示的失败原因
     * @return 带失败文案、数据为 null 的响应
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }

    /**
     * 创建同时带失败文案和业务数据的响应
     *
     * @param <T> 业务数据的类型
     * @param message 供前端展示的失败原因
     * @param data 失败时需要一并返回的业务数据
     * @return 带指定文案和数据的失败响应
     */
    public static <T> ApiResponse<T> fail(String message, T data) {
        return new ApiResponse<>(false, message, data);
    }
}
