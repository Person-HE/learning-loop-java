package com.closeloop.common;

import org.springframework.http.HttpStatus;

/**
 * 统一业务异常：携带错误码与 HTTP 状态。
 * 错误码契约与 Node 版一致：AUTH→401、RATE→429、其余默认 500/400/404。
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ApiException auth(String msg) { return new ApiException(msg, "AUTH", HttpStatus.UNAUTHORIZED); }
    public static ApiException rate(String msg) { return new ApiException(msg, "RATE", HttpStatus.TOO_MANY_REQUESTS); }
    public static ApiException badRequest(String msg) { return new ApiException(msg, null, HttpStatus.BAD_REQUEST); }
    public static ApiException notFound(String msg) { return new ApiException(msg, null, HttpStatus.NOT_FOUND); }
    public static ApiException internal(String msg) { return new ApiException(msg, null, HttpStatus.INTERNAL_SERVER_ERROR); }
    public static ApiException withCode(String msg, String code) {
        if ("AUTH".equals(code)) return auth(msg);
        if ("RATE".equals(code)) return rate(msg);
        return new ApiException(msg, code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status; }
}
