package com.ycf.gateway.config.center.spi;

import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public class ServiceLoaderConfigCenterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoaderConfigCenterRegistry.class);

    public ConfigCenter create(GatewayConfig gatewayConfig, ConfigLoader configLoader) {
        List<ConfigCenterFactory> factories = new ArrayList<>();
        ServiceLoader<ConfigCenterFactory> loader = ServiceLoader.load(ConfigCenterFactory.class);
        for (ConfigCenterFactory factory : loader) {
            factories.add(factory);
            log.info("config center factory loaded via spi, name={}, class={}",
                    factory.name(),
                    factory.getClass().getName());
        }

        factories.sort(Comparator.comparingInt(ConfigCenterFactory::order).reversed());
        for (ConfigCenterFactory factory : factories) {
            if (!factory.supports(gatewayConfig)) {
                continue;
            }
            log.info("config center selected via spi, name={}", factory.name());
            return factory.create(gatewayConfig, configLoader);
        }

        throw new IllegalStateException("no config center implementation available via ServiceLoader");
    }
}
