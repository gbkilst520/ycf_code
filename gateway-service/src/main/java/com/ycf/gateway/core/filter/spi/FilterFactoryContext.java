package com.ycf.gateway.core.filter.spi;

import com.ycf.gateway.config.model.GatewayConfig;
import com.ycf.gateway.core.ops.GatewayMetricsCollector;

public class FilterFactoryContext {

    private final GatewayConfig gatewayConfig;
    private final GatewayMetricsCollector metricsCollector;

    public FilterFactoryContext(GatewayConfig gatewayConfig, GatewayMetricsCollector metricsCollector) {
        this.gatewayConfig = gatewayConfig;
        this.metricsCollector = metricsCollector;
    }

    public GatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    public GatewayMetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
}
