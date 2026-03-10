package com.ycf.gateway.config.model;

import java.util.HashSet;
import java.util.Set;

public class GrayRule {

    private final String id;
    private final String sourceServiceId;
    private final String targetServiceId;
    private final String headerName;
    private final String headerValue;
    private final String cookieName;
    private final String cookieValue;
    private final Set<String> ipSet;
    private final int priority;

    public GrayRule(String id,
                    String sourceServiceId,
                    String targetServiceId,
                    String headerName,
                    String headerValue,
                    String cookieName,
                    String cookieValue,
                    Set<String> ipSet,
                    int priority) {
        this.id = id;
        this.sourceServiceId = sourceServiceId;
        this.targetServiceId = targetServiceId;
        this.headerName = normalize(headerName);
        this.headerValue = normalize(headerValue);
        this.cookieName = normalize(cookieName);
        this.cookieValue = normalize(cookieValue);
        this.priority = priority;

        Set<String> copiedIps = new HashSet<>();
        if (ipSet != null) {
            for (String ip : ipSet) {
                if (ip != null && !ip.isBlank()) {
                    copiedIps.add(ip.trim());
                }
            }
        }
        this.ipSet = Set.copyOf(copiedIps);
    }

    public String getId() {
        return id;
    }

    public String getSourceServiceId() {
        return sourceServiceId;
    }

    public String getTargetServiceId() {
        return targetServiceId;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public String getCookieName() {
        return cookieName;
    }

    public String getCookieValue() {
        return cookieValue;
    }

    public Set<String> getIpSet() {
        return ipSet;
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasHeaderCondition() {
        return headerName != null;
    }

    public boolean hasCookieCondition() {
        return cookieName != null;
    }

    public boolean hasIpCondition() {
        return !ipSet.isEmpty();
    }

    public boolean hasAnyCondition() {
        return hasHeaderCondition() || hasCookieCondition() || hasIpCondition();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
