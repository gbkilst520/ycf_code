package com.ycf.gateway.core.filter.spi.factory;

import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.impl.FlowFilter;
import com.ycf.gateway.core.filter.spi.FilterFactoryContext;
import com.ycf.gateway.core.filter.spi.GatewayFilterFactory;

public class FlowFilterFactory implements GatewayFilterFactory {

    @Override
    public String name() {
        return "flow";
    }

    @Override
    public GatewayFilter create(FilterFactoryContext context) {
        return new FlowFilter(context.getGatewayConfig().getFlow());
    }
}
