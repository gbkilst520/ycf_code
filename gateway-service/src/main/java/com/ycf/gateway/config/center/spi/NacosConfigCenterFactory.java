package com.ycf.gateway.config.center.spi;

import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.center.impl.NacosConfigCenter;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.GatewayConfig;

public class NacosConfigCenterFactory implements ConfigCenterFactory {

    @Override
    public String name() {
        return "nacos";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(GatewayConfig gatewayConfig) {
        return gatewayConfig.getNacos().getConfig().isEnabled();
    }

    @Override
    public ConfigCenter create(GatewayConfig gatewayConfig, ConfigLoader configLoader) {
        return new NacosConfigCenter(gatewayConfig.getNacos(), configLoader);
    }
}
