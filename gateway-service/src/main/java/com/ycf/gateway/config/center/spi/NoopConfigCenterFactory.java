package com.ycf.gateway.config.center.spi;

import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.center.impl.NoopConfigCenter;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.GatewayConfig;

public class NoopConfigCenterFactory implements ConfigCenterFactory {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public boolean supports(GatewayConfig gatewayConfig) {
        return true;
    }

    @Override
    public ConfigCenter create(GatewayConfig gatewayConfig, ConfigLoader configLoader) {
        return new NoopConfigCenter();
    }
}
