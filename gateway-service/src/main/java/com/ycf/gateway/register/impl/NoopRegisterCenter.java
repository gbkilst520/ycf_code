package com.ycf.gateway.register.impl;

import com.ycf.gateway.register.RegisterCenter;
import com.ycf.gateway.register.ServiceInstance;

import java.util.Collection;
import java.util.List;

public class NoopRegisterCenter implements RegisterCenter {

    @Override
    public void start(Collection<String> initialServiceIds) {
    }

    @Override
    public void watchService(String serviceId) {
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return List.of();
    }

    @Override
    public Collection<String> getWatchedServiceIds() {
        return List.of();
    }

    @Override
    public void close() {
    }
}
