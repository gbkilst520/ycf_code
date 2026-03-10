package com.ycf.gateway.core.filter.impl;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.config.model.ResilienceFallbackProperties;
import com.ycf.gateway.config.model.ResilienceProperties;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.repository.RouteRepository;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.resilience.ResilienceExecutor;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RouteFilter implements GatewayFilter {

    private final RouteRepository routeRepository;
    private final ResilienceExecutor resilienceExecutor;
    private final ResilienceProperties resilienceProperties;

    public RouteFilter(RouteRepository routeRepository,
                       ResilienceExecutor resilienceExecutor,
                       ResilienceProperties resilienceProperties) {
        this.routeRepository = routeRepository;
        this.resilienceExecutor = resilienceExecutor;
        this.resilienceProperties = resilienceProperties;
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        Optional<RouteDefinition> matchedRoute = routeRepository.findByPath(context.getPath());
        if (matchedRoute.isEmpty()) {
            return CompletableFuture.completedFuture(
                    HttpResponseFactory.error(ResponseCode.NOT_FOUND, "no route for path: " + context.getPath())
            );
        }

        RouteDefinition routeDefinition = matchedRoute.get();
        context.setRouteDefinition(routeDefinition);
        context.setResolvedServiceId(routeDefinition.getServiceId());

        return resilienceExecutor.execute(
                routeDefinition.getId(),
                routeDefinition.getServiceId(),
                () -> chain.filter(context),
                this::fallbackResponse
        );
    }

    private FullHttpResponse fallbackResponse() {
        ResilienceFallbackProperties fallbackProperties = resilienceProperties.getFallback();
        HttpResponseStatus status;
        try {
            status = HttpResponseStatus.valueOf(fallbackProperties.getStatusCode());
        } catch (Exception ex) {
            status = HttpResponseStatus.SERVICE_UNAVAILABLE;
        }
        return HttpResponseFactory.text(status, fallbackProperties.getBody());
    }
}
