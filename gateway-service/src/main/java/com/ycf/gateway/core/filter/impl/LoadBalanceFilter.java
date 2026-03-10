package com.ycf.gateway.core.filter.impl;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.model.ServiceDefinition;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.loadbalance.LoadBalancer;
import com.ycf.gateway.core.loadbalance.LoadBalancerRegistry;
import com.ycf.gateway.core.util.HttpResponseFactory;
import com.ycf.gateway.register.RegisterCenter;
import com.ycf.gateway.register.ServiceInstance;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LoadBalanceFilter implements GatewayFilter {

    private final Map<String, ServiceDefinition> staticServiceMap;
    private final RegisterCenter registerCenter;
    private final LoadBalancerRegistry loadBalancerRegistry;

    public LoadBalanceFilter(List<ServiceDefinition> serviceDefinitions,
                             RegisterCenter registerCenter,
                             LoadBalancerRegistry loadBalancerRegistry) {
        this.registerCenter = registerCenter;
        this.loadBalancerRegistry = loadBalancerRegistry;
        this.staticServiceMap = new HashMap<>();

        if (serviceDefinitions != null) {
            for (ServiceDefinition serviceDefinition : serviceDefinitions) {
                this.staticServiceMap.put(serviceDefinition.getServiceId(), serviceDefinition);
            }
        }
    }

    @Override
    public CompletableFuture<FullHttpResponse> filter(GatewayContext context, GatewayFilterChain chain) {
        RouteDefinition routeDefinition = context.getRouteDefinition();
        if (routeDefinition == null) {
            return CompletableFuture.completedFuture(
                    HttpResponseFactory.error(ResponseCode.INTERNAL_ERROR, "route missing before load balance")
            );
        }

        String serviceId = context.getResolvedServiceId();
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = routeDefinition.getServiceId();
            context.setResolvedServiceId(serviceId);
        }

        registerCenter.watchService(serviceId);

        List<ServiceInstance> discoveredInstances = registerCenter.getInstances(serviceId);
        if (!discoveredInstances.isEmpty()) {
            LoadBalanceType loadBalanceType = routeDefinition.getLoadBalanceType();
            LoadBalancer loadBalancer = loadBalancerRegistry.get(loadBalanceType);
            ServiceInstance selected = loadBalancer.choose(serviceId, discoveredInstances);
            if (selected == null) {
                return CompletableFuture.completedFuture(
                        HttpResponseFactory.error(ResponseCode.SERVICE_UNAVAILABLE,
                                "no available instance after load balance: " + serviceId)
                );
            }
            context.setSelectedInstance(selected);
            context.setTargetBaseUrl(selected.baseUrl());
            return chain.filter(context);
        }

        ServiceDefinition staticService = staticServiceMap.get(serviceId);
        if (staticService == null || !staticService.hasBaseUrl()) {
            return CompletableFuture.completedFuture(
                    HttpResponseFactory.error(ResponseCode.SERVICE_UNAVAILABLE,
                            "no instance discovered and no static baseUrl for service: " + serviceId)
            );
        }

        context.setServiceDefinition(staticService);
        context.setTargetBaseUrl(staticService.getBaseUrl());
        return chain.filter(context);
    }
}
