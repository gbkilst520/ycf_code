package com.ycf.gateway.bootstrap;

import com.ycf.common.constants.GatewayConstants;
import com.ycf.gateway.config.center.ConfigCenter;
import com.ycf.gateway.config.center.spi.ServiceLoaderConfigCenterRegistry;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.GatewayConfig;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.repository.InMemoryRouteRepository;
import com.ycf.gateway.config.repository.RouteRepository;
import com.ycf.gateway.core.access.NettyHttpServer;
import com.ycf.gateway.core.filter.FilterChainFactory;
import com.ycf.gateway.core.filter.GatewayFilter;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.filter.impl.ForwardFilter;
import com.ycf.gateway.core.filter.impl.LoadBalanceFilter;
import com.ycf.gateway.core.filter.impl.RouteFilter;
import com.ycf.gateway.core.filter.spi.FilterFactoryContext;
import com.ycf.gateway.core.filter.spi.ServiceLoaderFilterRegistry;
import com.ycf.gateway.core.forward.NettyHttpClient;
import com.ycf.gateway.core.loadbalance.LoadBalancerRegistry;
import com.ycf.gateway.core.ops.GatewayControlPlane;
import com.ycf.gateway.core.ops.GatewayMetricsCollector;
import com.ycf.gateway.core.resilience.ResilienceExecutor;
import com.ycf.gateway.register.RegisterCenter;
import com.ycf.gateway.register.impl.NacosRegisterCenter;
import com.ycf.gateway.register.impl.NoopRegisterCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GatewayBootstrap {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrap.class);

    public static void main(String[] args) throws Exception {
        String configFile = System.getProperty("gateway.config", GatewayConstants.CONFIG_FILE_DEFAULT);

        ConfigLoader configLoader = new ConfigLoader();
        GatewayConfig gatewayConfig = configLoader.load(configFile);

        RouteRepository routeRepository = new InMemoryRouteRepository(gatewayConfig.getRoutes());
        RegisterCenter registerCenter = createRegisterCenter(gatewayConfig);
        ConfigCenter configCenter = createConfigCenter(gatewayConfig, configLoader);

        registerCenter.start(routeRepository.allServiceIds());
        configCenter.start(newRoutes -> {
            routeRepository.replaceAll(newRoutes);
            registerCenter.watchServices(extractServiceIds(newRoutes));
            log.info("route repository refreshed from config center, routeCount={}", newRoutes.size());
        });

        NettyHttpClient nettyHttpClient = new NettyHttpClient();
        LoadBalancerRegistry loadBalancerRegistry = new LoadBalancerRegistry();
        ResilienceExecutor resilienceExecutor = new ResilienceExecutor(gatewayConfig.getResilience());
        GatewayMetricsCollector metricsCollector = new GatewayMetricsCollector();

        RouteFilter routeFilter = new RouteFilter(routeRepository, resilienceExecutor, gatewayConfig.getResilience());
        LoadBalanceFilter loadBalanceFilter = new LoadBalanceFilter(
                gatewayConfig.getServices(),
                registerCenter,
                loadBalancerRegistry
        );
        ForwardFilter forwardFilter = new ForwardFilter(nettyHttpClient);

        Map<String, GatewayFilter> filterRegistry = new ServiceLoaderFilterRegistry().load(
                new FilterFactoryContext(gatewayConfig, metricsCollector)
        );

        FilterChainFactory filterChainFactory = new FilterChainFactory(gatewayConfig.getFilterChain());
        GatewayFilterChain filterChain = filterChainFactory.create(
                routeFilter,
                loadBalanceFilter,
                forwardFilter,
                filterRegistry
        );

        GatewayControlPlane controlPlane = new GatewayControlPlane(
                routeRepository,
                registerCenter,
                gatewayConfig.getServices(),
                configLoader,
                metricsCollector
        );

        NettyHttpServer nettyHttpServer = new NettyHttpServer(
                gatewayConfig.getPort(),
                filterChain,
                controlPlane,
                metricsCollector
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("gateway shutdown start");
            try {
                nettyHttpServer.close();
                configCenter.close();
                registerCenter.close();
                resilienceExecutor.close();
                nettyHttpClient.close();
                metricsCollector.close();
            } catch (Exception ex) {
                log.error("gateway shutdown failed", ex);
            }
        }));

        nettyHttpServer.start();
        nettyHttpServer.blockUntilShutdown();
    }

    private static RegisterCenter createRegisterCenter(GatewayConfig gatewayConfig) {
        if (gatewayConfig.getNacos().getRegister().isEnabled()) {
            return new NacosRegisterCenter(gatewayConfig.getNacos());
        }
        return new NoopRegisterCenter();
    }

    private static ConfigCenter createConfigCenter(GatewayConfig gatewayConfig, ConfigLoader configLoader) {
        return new ServiceLoaderConfigCenterRegistry().create(gatewayConfig, configLoader);
    }

    private static Set<String> extractServiceIds(List<RouteDefinition> routes) {
        Set<String> serviceIds = new LinkedHashSet<>();
        for (RouteDefinition route : routes) {
            serviceIds.add(route.getServiceId());
        }
        return serviceIds;
    }
}
