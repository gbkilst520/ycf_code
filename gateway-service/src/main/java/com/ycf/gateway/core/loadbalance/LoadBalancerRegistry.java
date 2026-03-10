package com.ycf.gateway.core.loadbalance;

import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.core.loadbalance.impl.RandomLoadBalancer;
import com.ycf.gateway.core.loadbalance.impl.RoundRobinLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

public class LoadBalancerRegistry {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerRegistry.class);
    private final Map<LoadBalanceType, LoadBalancer> registry = new EnumMap<>(LoadBalanceType.class);

    public LoadBalancerRegistry() {
        ServiceLoader<LoadBalancer> serviceLoader = ServiceLoader.load(LoadBalancer.class);
        for (LoadBalancer loadBalancer : serviceLoader) {
            LoadBalanceType type = loadBalancer.type();
            if (type == null) {
                continue;
            }
            LoadBalancer previous = registry.put(type, loadBalancer);
            if (previous != null) {
                log.warn("duplicate load balancer type detected, overwrite previous implementation: {}", type);
            }
            log.info("load balancer loaded via spi, type={}, class={}", type, loadBalancer.getClass().getName());
        }

        registry.putIfAbsent(LoadBalanceType.ROUND_ROBIN, new RoundRobinLoadBalancer());
        registry.putIfAbsent(LoadBalanceType.RANDOM, new RandomLoadBalancer());
    }

    public LoadBalancer get(LoadBalanceType loadBalanceType) {
        LoadBalanceType effectiveType = loadBalanceType == null ? LoadBalanceType.ROUND_ROBIN : loadBalanceType;
        return registry.getOrDefault(effectiveType, registry.get(LoadBalanceType.ROUND_ROBIN));
    }
}
