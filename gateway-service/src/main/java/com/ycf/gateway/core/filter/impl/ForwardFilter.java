package com.ycf.gateway.core.filter.impl;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.forward.NettyHttpClient;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.concurrent.CompletableFuture;

public class ForwardFilter implements GatewayFilter {

    private final NettyHttpClient nettyHttpClient;

    public ForwardFilter(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = nettyHttpClient;
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        return nettyHttpClient.forward(context)
                .exceptionally(ex -> HttpResponseFactory.error(
                        ResponseCode.INTERNAL_ERROR,
                        "forward request failed"
                ));
    }
}
