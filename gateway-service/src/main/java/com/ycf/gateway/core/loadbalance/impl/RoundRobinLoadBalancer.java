package com.ycf.gateway.core.loadbalance.impl;

import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.core.loadbalance.LoadBalancer;
import com.ycf.gateway.register.ServiceInstance;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {

    private final ConcurrentMap<String, AtomicInteger> sequenceByService = new ConcurrentHashMap<>();

    @Override
    public LoadBalanceType type() {
        return LoadBalanceType.ROUND_ROBIN;
    }

    @Override
    public ServiceInstance choose(String serviceId, List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        AtomicInteger sequence = sequenceByService.computeIfAbsent(serviceId, key -> new AtomicInteger(0));
        int index = Math.floorMod(sequence.getAndIncrement(), instances.size());
        return instances.get(index);
    }
}
