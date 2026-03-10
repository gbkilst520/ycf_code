package com.ycf.gateway.config.center;

import com.ycf.gateway.config.model.RouteDefinition;

import java.util.List;
import java.util.function.Consumer;

public interface ConfigCenter extends AutoCloseable {

    void start(Consumer<List<RouteDefinition>> routeRefreshCallback);

    @Override
    void close();
}
