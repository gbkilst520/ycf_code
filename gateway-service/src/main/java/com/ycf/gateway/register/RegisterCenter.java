package com.ycf.gateway.register;

import java.util.Collection;
import java.util.List;

public interface RegisterCenter extends AutoCloseable {

    void start(Collection<String> initialServiceIds);

    void watchService(String serviceId);

    default void watchServices(Collection<String> serviceIds) {
        if (serviceIds == null) {
            return;
        }
        for (String serviceId : serviceIds) {
            watchService(serviceId);
        }
    }

    default Collection<String> getWatchedServiceIds() {
        return List.of();
    }

    List<ServiceInstance> getInstances(String serviceId);

    @Override
    void close();
}
