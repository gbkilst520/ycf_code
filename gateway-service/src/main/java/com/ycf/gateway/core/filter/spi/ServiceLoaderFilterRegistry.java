package com.ycf.gateway.core.filter.spi;

import com.ycf.gateway.core.filter.GatewayFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ServiceLoaderFilterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoaderFilterRegistry.class);

    public Map<String, GatewayFilter> load(FilterFactoryContext context) {
        Map<String, GatewayFilter> registry = new LinkedHashMap<>();
        ServiceLoader<GatewayFilterFactory> serviceLoader = ServiceLoader.load(GatewayFilterFactory.class);
        for (GatewayFilterFactory factory : serviceLoader) {
            String name = factory.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            GatewayFilter filter = factory.create(context);
            if (filter == null) {
                continue;
            }
            GatewayFilter previous = registry.put(name, filter);
            if (previous != null) {
                log.warn("duplicate filter factory name detected, overwrite previous implementation: {}", name);
            }
            log.info("gateway filter factory loaded via spi, name={}, factory={}",
                    name,
                    factory.getClass().getName());
        }
        return registry;
    }
}
