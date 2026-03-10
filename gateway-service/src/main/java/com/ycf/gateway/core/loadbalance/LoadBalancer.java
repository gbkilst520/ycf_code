package com.ycf.gateway.core.loadbalance;

import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.register.ServiceInstance;

import java.util.List;

public interface LoadBalancer {

    LoadBalanceType type();

    ServiceInstance choose(String serviceId, List<ServiceInstance> instances);
}
