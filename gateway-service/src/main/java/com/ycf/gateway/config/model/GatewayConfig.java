package com.ycf.gateway.config.model;

import java.util.Collections;
import java.util.List;

public class GatewayConfig {

    private final int port;
    private final List<RouteDefinition> routes;
    private final List<ServiceDefinition> services;
    private final NacosProperties nacos;
    private final FilterChainProperties filterChain;
    private final FlowProperties flow;
    private final ResilienceProperties resilience;
    private final GrayProperties gray;

    public GatewayConfig(int port,
                         List<RouteDefinition> routes,
                         List<ServiceDefinition> services,
                         NacosProperties nacos,
                         FilterChainProperties filterChain,
                         FlowProperties flow,
                         ResilienceProperties resilience,
                         GrayProperties gray) {
        this.port = port;
        this.routes = routes == null ? List.of() : List.copyOf(routes);
        this.services = services == null ? List.of() : List.copyOf(services);
        this.nacos = nacos;
        this.filterChain = filterChain;
        this.flow = flow;
        this.resilience = resilience;
        this.gray = gray;
    }

    public int getPort() {
        return port;
    }

    public List<RouteDefinition> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    public List<ServiceDefinition> getServices() {
        return Collections.unmodifiableList(services);
    }

    public NacosProperties getNacos() {
        return nacos;
    }

    public FilterChainProperties getFilterChain() {
        return filterChain;
    }

    public FlowProperties getFlow() {
        return flow;
    }

    public ResilienceProperties getResilience() {
        return resilience;
    }

    public GrayProperties getGray() {
        return gray;
    }
}
