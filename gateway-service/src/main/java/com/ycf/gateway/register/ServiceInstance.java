package com.ycf.gateway.register;

import java.util.Map;

public class ServiceInstance {

    private final String serviceId;
    private final String host;
    private final int port;
    private final Map<String, String> metadata;

    public ServiceInstance(String serviceId, String host, int port, Map<String, String> metadata) {
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String baseUrl() {
        String scheme = metadata.getOrDefault("scheme", "http");
        return scheme + "://" + host + ":" + port;
    }

    public String identity() {
        return host + ":" + port;
    }
}
