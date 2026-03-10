package com.ycf.gateway.config.model;

public class ServiceDefinition {

    private String serviceId;
    private String baseUrl;

    public ServiceDefinition() {
    }

    public ServiceDefinition(String serviceId, String baseUrl) {
        this.serviceId = serviceId;
        this.baseUrl = baseUrl;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean hasBaseUrl() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
