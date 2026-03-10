package com.ycf.gateway.core.filter.impl;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.config.model.FlowProperties;
import com.ycf.gateway.config.model.RateLimitRule;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.filter.support.TokenBucket;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FlowFilter implements GatewayFilter {

    private final FlowProperties flowProperties;
    private final TokenBucket globalBucket;
    private final Map<String, TokenBucket> serviceBuckets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    public FlowFilter(FlowProperties flowProperties) {
        this.flowProperties = flowProperties;
        this.globalBucket = newBucket(flowProperties.getGlobalRule());
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        if (!flowProperties.isEnabled()) {
            return chain.filter(context);
        }

        if (globalBucket != null && !globalBucket.tryAcquire()) {
            return CompletableFuture.completedFuture(
                    HttpResponseFactory.error(ResponseCode.TOO_MANY_REQUESTS, "global rate limit exceeded")
            );
        }

        String clientIp = context.getClientIp();
        RateLimitRule ipRule = flowProperties.getIpRules().get(clientIp);
        if (ipRule != null) {
            TokenBucket ipBucket = ipBuckets.computeIfAbsent("ip:" + clientIp, key -> newBucket(ipRule));
            if (ipBucket != null && !ipBucket.tryAcquire()) {
                return CompletableFuture.completedFuture(
                        HttpResponseFactory.error(ResponseCode.TOO_MANY_REQUESTS,
                                "ip rate limit exceeded: " + clientIp)
                );
            }
        }

        String serviceId = context.getResolvedServiceId();
        if (serviceId != null && !serviceId.isBlank()) {
            RateLimitRule serviceRule = flowProperties.getServiceRules().get(serviceId);
            if (serviceRule != null) {
                TokenBucket serviceBucket = serviceBuckets.computeIfAbsent(
                        "svc:" + serviceId,
                        key -> newBucket(serviceRule)
                );
                if (serviceBucket != null && !serviceBucket.tryAcquire()) {
                    return CompletableFuture.completedFuture(
                            HttpResponseFactory.error(ResponseCode.TOO_MANY_REQUESTS,
                                    "service rate limit exceeded: " + serviceId)
                    );
                }
            }
        }

        return chain.filter(context);
    }

    private TokenBucket newBucket(RateLimitRule rule) {
        if (rule == null || !rule.isEnabled()) {
            return null;
        }
        return new TokenBucket(rule.getRate(), rule.getCapacity());
    }
}
