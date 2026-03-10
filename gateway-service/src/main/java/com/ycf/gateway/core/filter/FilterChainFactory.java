package com.ycf.gateway.core.filter;

import com.ycf.common.enums.ResponseCode;
import com.ycf.common.exception.GatewayException;
import com.ycf.gateway.config.model.FilterChainProperties;
import com.ycf.gateway.core.filter.impl.DefaultGatewayFilterChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FilterChainFactory {

    private final FilterChainProperties filterChainProperties;

    public FilterChainFactory(FilterChainProperties filterChainProperties) {
        this.filterChainProperties = filterChainProperties;
    }

    public GatewayFilterChain create(GatewayFilter routeFilter,
                                     GatewayFilter loadBalanceFilter,
                                     GatewayFilter forwardFilter,
                                     Map<String, GatewayFilter> filterRegistry) {
        List<GatewayFilter> filters = new ArrayList<>();
        filters.add(routeFilter);

        for (String preName : filterChainProperties.getPreFilters()) {
            GatewayFilter preFilter = filterRegistry.get(preName);
            if (preFilter == null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "unknown pre filter in filter chain config: " + preName);
            }
            filters.add(preFilter);
        }

        filters.add(loadBalanceFilter);

        for (String postName : filterChainProperties.getPostFilters()) {
            GatewayFilter postFilter = filterRegistry.get(postName);
            if (postFilter == null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "unknown post filter in filter chain config: " + postName);
            }
            filters.add(postFilter);
        }

        filters.add(forwardFilter);
        return new DefaultGatewayFilterChain(filters);
    }
}
