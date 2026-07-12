package com.learnspark.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learnspark.common.exception.ErrorCode;
import lombok.Data;

/**
 * 统一响应结构。
 *
 * <pre>
 * 成功：{ "code": 0, "message": "ok", "data": {...} }
 * 失败：{ "code": 40001, "message": "邮箱已注册", "data": null }
 * </pre>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    /** 业务码：0 表示成功，非 0 表示失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(0, message, data);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode) {
        return new ApiResult<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.getCode(), message, null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
