package com.ycf.common.enums;

import java.util.Arrays;

public enum ResponseCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "bad request"),
    TOO_MANY_REQUESTS(429, "too many requests"),
    NOT_FOUND(404, "route not found"),
    SERVICE_UNAVAILABLE(503, "service unavailable"),
    INTERNAL_ERROR(500, "internal server error");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ResponseCode fromCode(int code) {
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(INTERNAL_ERROR);
    }
}
