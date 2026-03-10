package com.ycf.gateway.core.filter.spi.factory;

import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.impl.GrayFilter;
import com.ycf.gateway.core.filter.spi.FilterFactoryContext;
import com.ycf.gateway.core.filter.spi.GatewayFilterFactory;

public class GrayFilterFactory implements GatewayFilterFactory {

    @Override
    public String name() {
        return "gray";
    }

    @Override
    public GatewayFilter create(FilterFactoryContext context) {
        return new GrayFilter(context.getGatewayConfig().getGray());
    }
}
