package com.ycf.gateway.core.filter.impl;

import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.util.concurrent.CompletableFuture;

public class ResponseHeaderPostFilter implements GatewayFilter {

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        return chain.filter(context).thenApply(response -> {
            response.headers().set(HttpHeaderNames.SERVER, "ycf-netty-gateway");
            response.headers().set("X-Gateway-Service", "gateway-service");
            return response;
        });
    }
}
