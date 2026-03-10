package com.ycf.gateway.core.filter.spi.factory;

import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.impl.AccessLogPostFilter;
import com.ycf.gateway.core.filter.spi.FilterFactoryContext;
import com.ycf.gateway.core.filter.spi.GatewayFilterFactory;

public class AccessLogPostFilterFactory implements GatewayFilterFactory {

    @Override
    public String name() {
        return "accessLog";
    }

    @Override
    public GatewayFilter create(FilterFactoryContext context) {
        return new AccessLogPostFilter();
    }
}
