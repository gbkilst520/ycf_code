package com.ycf.gateway.config.repository;

import com.ycf.common.enums.ResponseCode;
import com.ycf.common.exception.GatewayException;
import com.ycf.gateway.config.model.RouteDefinition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryRouteRepository implements RouteRepository {

    private final AtomicReference<List<RouteDefinition>> routeRef;

    public InMemoryRouteRepository(List<RouteDefinition> initRoutes) {
        validate(initRoutes);
        this.routeRef = new AtomicReference<>(List.copyOf(initRoutes));
    }

    @Override
    public List<RouteDefinition> getAllRoutes() {
        return routeRef.get();
    }

    @Override
    public Optional<RouteDefinition> findByPath(String path) {
        return routeRef.get().stream().filter(route -> route.matches(path)).findFirst();
    }

    @Override
    public Set<String> allServiceIds() {
        Set<String> serviceIds = new LinkedHashSet<>();
        for (RouteDefinition routeDefinition : routeRef.get()) {
            serviceIds.add(routeDefinition.getServiceId());
        }
        return serviceIds;
    }

    @Override
    public void replaceAll(List<RouteDefinition> routes) {
        validate(routes);
        routeRef.set(List.copyOf(routes));
    }

    private void validate(List<RouteDefinition> routes) {
        if (routes == null || routes.isEmpty()) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "route repository can not be empty");
        }

        Set<String> routeIds = new LinkedHashSet<>();
        for (RouteDefinition route : routes) {
            if (route == null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "route item can not be null");
            }
            if (!routeIds.add(route.getId())) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "duplicated route id in repository: " + route.getId());
            }
            if (route.getPath() == null || route.getPath().isBlank()) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "route path can not be blank: " + route.getId());
            }
            if (route.getServiceId() == null || route.getServiceId().isBlank()) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "route serviceId can not be blank: " + route.getId());
            }
        }
    }
}
