package com.ycf.gateway.core.filter.impl;

import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.register.ServiceInstance;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AccessLogPostFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogPostFilter.class);

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        return chain.filter(context).whenComplete((response, throwable) -> {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - context.getRequestStartNanos());
            String resolvedService = context.getResolvedServiceId() == null ? "-" : context.getResolvedServiceId();
            RouteDefinition routeDefinition = context.getRouteDefinition();
            ServiceInstance selectedInstance = context.getSelectedInstance();
            String selectedEndpoint = selectedInstance == null ? "-" : selectedInstance.identity();
            int requestBytes = context.getRequest().content().readableBytes();
            int responseBytes = response == null ? 0 : response.content().readableBytes();
            String userAgent = headerOrDefault(context, HttpHeaderNames.USER_AGENT.toString(), "-");
            String traceParent = headerOrDefault(context, "traceparent", "-");
            String xB3TraceId = headerOrDefault(context, "X-B3-TraceId", "-");
            if (throwable != null) {
                log.warn("gateway access failed, requestId={}, method={}, uri={}, path={}, query={}, routeId={}, "
                                + "service={}, targetBaseUrl={}, selectedInstance={}, ip={}, costMs={}, reqBytes={}, "
                                + "respBytes={}, userAgent={}, traceParent={}, b3TraceId={}",
                        context.getRequestId(),
                        context.getMethod().name(),
                        context.getUri(),
                        context.getPath(),
                        context.getQuery(),
                        routeDefinition == null ? "-" : routeDefinition.getId(),
                        resolvedService,
                        context.getTargetBaseUrl() == null ? "-" : context.getTargetBaseUrl(),
                        selectedEndpoint,
                        context.getClientIp(),
                        costMs,
                        requestBytes,
                        responseBytes,
                        userAgent,
                        traceParent,
                        xB3TraceId,
                        throwable);
                return;
            }
            int status = response == null ? 500 : response.status().code();
            log.info("gateway access, requestId={}, method={}, uri={}, path={}, query={}, routeId={}, "
                            + "service={}, targetBaseUrl={}, selectedInstance={}, ip={}, status={}, costMs={}, "
                            + "reqBytes={}, respBytes={}, userAgent={}, traceParent={}, b3TraceId={}",
                    context.getRequestId(),
                    context.getMethod().name(),
                    context.getUri(),
                    context.getPath(),
                    context.getQuery(),
                    routeDefinition == null ? "-" : routeDefinition.getId(),
                    resolvedService,
                    context.getTargetBaseUrl() == null ? "-" : context.getTargetBaseUrl(),
                    selectedEndpoint,
                    context.getClientIp(),
                    status,
                    costMs,
                    requestBytes,
                    responseBytes,
                    userAgent,
                    traceParent,
                    xB3TraceId);
        });
    }

    private String headerOrDefault(GatewayContext context, String headerName, String fallback) {
        String value = context.getRequest().headers().get(headerName);
        return value == null || value.isBlank() ? fallback : value;
    }
}
