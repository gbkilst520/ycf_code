package com.ycf.gateway.register.impl;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.ycf.gateway.config.model.NacosProperties;
import com.ycf.gateway.register.RegisterCenter;
import com.ycf.gateway.register.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NacosRegisterCenter implements RegisterCenter {

    private static final Logger log = LoggerFactory.getLogger(NacosRegisterCenter.class);

    private final NacosProperties nacosProperties;
    private final ConcurrentMap<String, List<ServiceInstance>> instancesByService = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, EventListener> listenerByService = new ConcurrentHashMap<>();
    private final Set<String> watchedServices = ConcurrentHashMap.newKeySet();
    private NamingService namingService;

    public NacosRegisterCenter(NacosProperties nacosProperties) {
        this.nacosProperties = nacosProperties;
    }

    @Override
    public synchronized void start(Collection<String> initialServiceIds) {
        if (!nacosProperties.getRegister().isEnabled()) {
            log.info("register center disabled, use static service definitions only");
            return;
        }
        ensureNamingService();
        watchServices(initialServiceIds);
    }

    @Override
    public synchronized void watchService(String serviceId) {
        if (!nacosProperties.getRegister().isEnabled()) {
            return;
        }
        if (serviceId == null || serviceId.isBlank()) {
            return;
        }
        ensureNamingService();

        if (!watchedServices.add(serviceId)) {
            return;
        }

        try {
            refreshInstances(serviceId);
            EventListener listener = new EventListener() {
                @Override
                public void onEvent(Event event) {
                    handleEvent(serviceId, event);
                }
            };
            listenerByService.put(serviceId, listener);
            namingService.subscribe(serviceId, nacosProperties.getRegister().getGroupName(), listener);
            log.info("nacos watch service subscribed, serviceId={}, group={}",
                    serviceId, nacosProperties.getRegister().getGroupName());
        } catch (Exception ex) {
            watchedServices.remove(serviceId);
            listenerByService.remove(serviceId);
            throw new IllegalStateException("failed to subscribe service from nacos: " + serviceId, ex);
        }
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        List<ServiceInstance> instances = instancesByService.get(serviceId);
        return instances == null ? List.of() : instances;
    }

    @Override
    public Collection<String> getWatchedServiceIds() {
        return Collections.unmodifiableSet(watchedServices);
    }

    @Override
    public synchronized void close() {
        if (!nacosProperties.getRegister().isEnabled()) {
            return;
        }
        if (namingService == null) {
            return;
        }

        for (String serviceId : watchedServices) {
            try {
                EventListener listener = listenerByService.remove(serviceId);
                if (listener != null) {
                    namingService.unsubscribe(serviceId, nacosProperties.getRegister().getGroupName(), listener);
                }
            } catch (Exception ex) {
                log.warn("failed to unsubscribe nacos service: {}", serviceId, ex);
            }
        }

        try {
            namingService.shutDown();
        } catch (NacosException ex) {
            log.warn("failed to shutdown nacos naming service", ex);
        }
    }

    private void ensureNamingService() {
        if (namingService != null) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, nacosProperties.getServerAddr());
            putIfNotBlank(properties, PropertyKeyConst.NAMESPACE, nacosProperties.getNamespace());
            putIfNotBlank(properties, PropertyKeyConst.USERNAME, nacosProperties.getUsername());
            putIfNotBlank(properties, PropertyKeyConst.PASSWORD, nacosProperties.getPassword());
            namingService = NacosFactory.createNamingService(properties);
            log.info("nacos naming service initialized, serverAddr={}", nacosProperties.getServerAddr());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize nacos naming service", ex);
        }
    }

    private void refreshInstances(String serviceId) throws NacosException {
        List<Instance> nacosInstances = namingService.selectInstances(
                serviceId,
                nacosProperties.getRegister().getGroupName(),
                true
        );
        updateInstances(serviceId, convertInstances(serviceId, nacosInstances), "refresh");
    }

    private void handleEvent(String serviceId, Event event) {
        if (!(event instanceof NamingEvent namingEvent)) {
            return;
        }
        updateInstances(serviceId, convertInstances(serviceId, namingEvent.getInstances()), "event");
    }

    private List<ServiceInstance> convertInstances(String serviceId, List<Instance> nacosInstances) {
        List<ServiceInstance> converted = new ArrayList<>();
        for (Instance instance : nacosInstances) {
            if (!instance.isEnabled() || !instance.isHealthy()) {
                continue;
            }
            converted.add(new ServiceInstance(
                    serviceId,
                    instance.getIp(),
                    instance.getPort(),
                    instance.getMetadata()
            ));
        }
        return List.copyOf(converted);
    }

    private void updateInstances(String serviceId, List<ServiceInstance> newInstances, String reason) {
        List<ServiceInstance> oldInstances = instancesByService.put(serviceId, newInstances);
        Set<String> oldSet = toIdentitySet(oldInstances);
        Set<String> newSet = toIdentitySet(newInstances);

        Set<String> online = new LinkedHashSet<>(newSet);
        online.removeAll(oldSet);

        Set<String> offline = new LinkedHashSet<>(oldSet);
        offline.removeAll(newSet);

        if (!online.isEmpty() || !offline.isEmpty()) {
            log.info("service instance changed, serviceId={}, reason={}, online={}, offline={}, total={}",
                    serviceId, reason, online, offline, newInstances.size());
        }
    }

    private Set<String> toIdentitySet(List<ServiceInstance> instances) {
        Set<String> identities = new LinkedHashSet<>();
        if (instances == null) {
            return identities;
        }
        for (ServiceInstance instance : instances) {
            identities.add(instance.identity());
        }
        return identities;
    }

    private void putIfNotBlank(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }
}
