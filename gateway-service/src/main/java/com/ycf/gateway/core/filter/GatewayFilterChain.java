package com.ycf.gateway.core.filter;

import com.ycf.gateway.core.access.GatewayContext;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface GatewayFilterChain {

    CompletableFuture<FullHttpResponse> filter(GatewayContext context);
}
