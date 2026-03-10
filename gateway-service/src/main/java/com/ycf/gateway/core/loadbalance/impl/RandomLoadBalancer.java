package com.ycf.gateway.core.loadbalance.impl;

import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.core.loadbalance.LoadBalancer;
import com.ycf.gateway.register.ServiceInstance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public LoadBalanceType type() {
        return LoadBalanceType.RANDOM;
    }

    @Override
    public ServiceInstance choose(String serviceId, List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(index);
    }
}
