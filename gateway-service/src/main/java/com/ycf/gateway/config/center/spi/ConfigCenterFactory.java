package com.ycf.gateway.config.center.spi;

import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.GatewayConfig;

public interface ConfigCenterFactory {

    String name();

    default int order() {
        return 0;
    }

    boolean supports(GatewayConfig gatewayConfig);

    ConfigCenter create(GatewayConfig gatewayConfig, ConfigLoader configLoader);
}
