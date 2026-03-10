package com.ycf.gateway.config.center.impl;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.NacosConfigProperties;
import com.ycf.gateway.config.model.NacosProperties;
import com.ycf.gateway.config.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class NacosConfigCenter implements ConfigCenter {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigCenter.class);

    private final NacosProperties nacosProperties;
    private final ConfigLoader configLoader;
    private ConfigService configService;
    private Listener listener;

    public NacosConfigCenter(NacosProperties nacosProperties, ConfigLoader configLoader) {
        this.nacosProperties = nacosProperties;
        this.configLoader = configLoader;
    }

    @Override
    public synchronized void start(Consumer<List<RouteDefinition>> routeRefreshCallback) {
        NacosConfigProperties configProperties = nacosProperties.getConfig();
        if (!configProperties.isEnabled()) {
            log.info("config center disabled, route source is local yaml");
            return;
        }

        ensureConfigService();
        String dataId = configProperties.getDataId();
        String group = configProperties.getGroup();

        listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                refreshRoutes(routeRefreshCallback, configInfo, "event");
            }
        };

        try {
            String initContent = configService.getConfig(dataId, group, configProperties.getTimeoutMs());
            refreshRoutes(routeRefreshCallback, initContent, "init");
            configService.addListener(dataId, group, listener);
            log.info("nacos config listener registered, dataId={}, group={}", dataId, group);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize nacos config center", ex);
        }
    }

    @Override
    public synchronized void close() {
        if (!nacosProperties.getConfig().isEnabled()) {
            return;
        }
        if (configService == null || listener == null) {
            return;
        }
        try {
            NacosConfigProperties configProperties = nacosProperties.getConfig();
            configService.removeListener(configProperties.getDataId(), configProperties.getGroup(), listener);
        } catch (Exception ex) {
            log.warn("failed to remove nacos config listener", ex);
        }
    }

    private void ensureConfigService() {
        if (configService != null) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, nacosProperties.getServerAddr());
            putIfNotBlank(properties, PropertyKeyConst.NAMESPACE, nacosProperties.getNamespace());
            putIfNotBlank(properties, PropertyKeyConst.USERNAME, nacosProperties.getUsername());
            putIfNotBlank(properties, PropertyKeyConst.PASSWORD, nacosProperties.getPassword());
            configService = NacosFactory.createConfigService(properties);
            log.info("nacos config service initialized, serverAddr={}", nacosProperties.getServerAddr());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize nacos config service", ex);
        }
    }

    private void refreshRoutes(Consumer<List<RouteDefinition>> routeRefreshCallback,
                               String rawConfig,
                               String source) {
        if (rawConfig == null || rawConfig.isBlank()) {
            log.warn("ignore empty route config from nacos, source={}", source);
            return;
        }

        try {
            List<RouteDefinition> routes = configLoader.loadRoutesFromText(rawConfig);
            routeRefreshCallback.accept(routes);
            log.info("dynamic routes refreshed, source={}, routeCount={}", source, routes.size());
        } catch (Exception ex) {
            log.error("dynamic route refresh rejected due to invalid config, source={}", source, ex);
        }
    }

    private void putIfNotBlank(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }
}
