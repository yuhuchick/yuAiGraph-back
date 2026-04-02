package com.knowledge.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一接口响应体。
 * <pre>
 * 成功: { "code": 0, "message": "ok", "data": <T> }
 * 失败: { "code": <http_status>, "message": "说明", "data": null }
 * </pre>
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    // ── 成功 ──────────────────────────────────────────────────

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    // ── 失败 ──────────────────────────────────────────────────

    public static <T> Result<T> fail(int httpStatus, String message) {
        return new Result<>(httpStatus, message, null);
    }
}
