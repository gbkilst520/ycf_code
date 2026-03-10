package com.ycf.gateway.config.repository;

import com.ycf.gateway.config.model.RouteDefinition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RouteRepository {

    List<RouteDefinition> getAllRoutes();

    Optional<RouteDefinition> findByPath(String path);

    Set<String> allServiceIds();

    void replaceAll(List<RouteDefinition> routes);
}
