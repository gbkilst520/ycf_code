package com.ycf.common.exception;

import com.ycf.common.enums.ResponseCode;

public class GatewayException extends RuntimeException {

    private final ResponseCode responseCode;

    public GatewayException(ResponseCode responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public GatewayException(ResponseCode responseCode, String message, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }
}
