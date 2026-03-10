package com.ycf.gateway.config.center.impl;

import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.model.RouteDefinition;

import java.util.List;
import java.util.function.Consumer;

public class NoopConfigCenter implements ConfigCenter {

    @Override
    public void start(Consumer<List<RouteDefinition>> routeRefreshCallback) {
    }

    @Override
    public void close() {
    }
}
