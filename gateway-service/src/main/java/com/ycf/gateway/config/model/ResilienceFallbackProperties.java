package com.ycf.gateway.config.model;

public class ResilienceFallbackProperties {

    private final int statusCode;
    private final String body;

    public ResilienceFallbackProperties(int statusCode, String body) {
        this.statusCode = statusCode <= 0 ? 503 : statusCode;
        this.body = body == null || body.isBlank() ? "Service temporarily unavailable" : body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
