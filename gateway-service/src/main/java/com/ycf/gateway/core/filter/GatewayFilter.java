package com.ycf.gateway.core.filter;

import com.ycf.gateway.core.access.GatewayContext;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.concurrent.CompletableFuture;

public interface GatewayFilter {

    CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain);
}
