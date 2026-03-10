package com.ycf.gateway.core.filter.spi.factory;

import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.impl.ResponseHeaderPostFilter;
import com.ycf.gateway.core.filter.spi.FilterFactoryContext;
import com.ycf.gateway.core.filter.spi.GatewayFilterFactory;

public class ResponseHeaderPostFilterFactory implements GatewayFilterFactory {

    @Override
    public String name() {
        return "responseHeader";
    }

    @Override
    public GatewayFilter create(FilterFactoryContext context) {
        return new ResponseHeaderPostFilter();
    }
}
