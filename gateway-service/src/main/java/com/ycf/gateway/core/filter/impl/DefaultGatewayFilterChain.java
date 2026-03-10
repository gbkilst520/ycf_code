package com.ycf.gateway.core.filter.impl;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DefaultGatewayFilterChain implements GatewayFilterChain {

    private final List<GatewayFilter> filters;

    public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
        this.filters = filters == null ? List.of() : List.copyOf(filters);
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context) {
        return new InternalChain(filters, 0).filter(context);
    }

    private static class InternalChain implements GatewayFilterChain {

        private final List<GatewayFilter> filters;
        private final int index;

        private InternalChain(List<GatewayFilter> filters, int index) {
            this.filters = filters;
            this.index = index;
        }

        @Override
        public CompletableFuture<FullHttpResponse> filter(GatewayContext context) {
            if (index >= filters.size()) {
                return CompletableFuture.completedFuture(
                        HttpResponseFactory.error(ResponseCode.INTERNAL_ERROR, "filter chain has no terminal filter")
                );
            }
            GatewayFilter current = filters.get(index);
            return current.filter(context, new InternalChain(filters, index + 1));
        }
    }
}
