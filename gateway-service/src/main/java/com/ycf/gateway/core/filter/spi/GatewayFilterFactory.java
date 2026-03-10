package com.ycf.gateway.core.filter.spi;

import com.ycf.gateway.core.filter.GatewayFilter;

public interface GatewayFilterFactory {

    String name();

    GatewayFilter create(FilterFactoryContext context);
}
