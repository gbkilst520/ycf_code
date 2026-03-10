package com.ycf.gateway.core.filter.impl;

import com.ycf.gateway.config.model.GrayProperties;
import com.ycf.gateway.config.model.GrayRule;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GrayFilter implements GatewayFilter {

    private final GrayProperties grayProperties;

    public GrayFilter(GrayProperties grayProperties) {
        this.grayProperties = grayProperties;
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        if (!grayProperties.isEnabled()) {
            return chain.filter(context);
        }

        String currentServiceId = context.getResolvedServiceId();
        if (currentServiceId == null || currentServiceId.isBlank()) {
            return chain.filter(context);
        }

        List<GrayRule> rules = grayProperties.getRules();
        for (GrayRule rule : rules) {
            if (!currentServiceId.equals(rule.getSourceServiceId())) {
                continue;
            }
            if (matches(context, rule)) {
                context.setResolvedServiceId(rule.getTargetServiceId());
                break;
            }
        }

        return chain.filter(context);
    }

    private boolean matches(GatewayContext context, GrayRule rule) {
        if (!rule.hasAnyCondition()) {
            return false;
        }

        boolean headerMatch = true;
        if (rule.hasHeaderCondition()) {
            String requestValue = context.getRequest().headers().get(rule.getHeaderName());
            if (rule.getHeaderValue() == null) {
                headerMatch = requestValue != null && !requestValue.isBlank();
            } else {
                headerMatch = rule.getHeaderValue().equals(requestValue);
            }
        }

        boolean cookieMatch = true;
        if (rule.hasCookieCondition()) {
            String cookieHeader = context.getRequest().headers().get(HttpHeaderNames.COOKIE);
            String cookieValue = parseCookie(cookieHeader).get(rule.getCookieName());
            if (rule.getCookieValue() == null) {
                cookieMatch = cookieValue != null && !cookieValue.isBlank();
            } else {
                cookieMatch = rule.getCookieValue().equals(cookieValue);
            }
        }

        boolean ipMatch = true;
        if (rule.hasIpCondition()) {
            ipMatch = rule.getIpSet().contains(context.getClientIp());
        }

        return headerMatch && cookieMatch && ipMatch;
    }

    private Map<String, String> parseCookie(String rawCookie) {
        if (rawCookie == null || rawCookie.isBlank()) {
            return Map.of();
        }
        Map<String, String> cookieMap = new HashMap<>();
        String[] parts = rawCookie.split(";");
        for (String part : parts) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank()) {
                cookieMap.put(pair[0].trim(), pair[1].trim());
            }
        }
        return cookieMap;
    }
}
