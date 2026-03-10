package com.ycf.gateway.config.model;

import java.util.ArrayList;
import java.util.List;

public class RouteDefinition {

    private String id;
    private String path;
    private String serviceId;
    private int stripPrefix;
    private LoadBalanceType loadBalanceType = LoadBalanceType.ROUND_ROBIN;

    public RouteDefinition() {
    }

    public RouteDefinition(String id,
                           String path,
                           String serviceId,
                           int stripPrefix,
                           LoadBalanceType loadBalanceType) {
        this.id = id;
        this.path = path;
        this.serviceId = serviceId;
        this.stripPrefix = stripPrefix;
        this.loadBalanceType = loadBalanceType == null ? LoadBalanceType.ROUND_ROBIN : loadBalanceType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public int getStripPrefix() {
        return stripPrefix;
    }

    public void setStripPrefix(int stripPrefix) {
        this.stripPrefix = stripPrefix;
    }

    public LoadBalanceType getLoadBalanceType() {
        return loadBalanceType;
    }

    public void setLoadBalanceType(LoadBalanceType loadBalanceType) {
        this.loadBalanceType = loadBalanceType == null ? LoadBalanceType.ROUND_ROBIN : loadBalanceType;
    }

    public boolean matches(String requestPath) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.endsWith("/**")) {
            String prefix = path.substring(0, path.length() - 3);
            if (prefix.isEmpty() || "/".equals(prefix)) {
                return true;
            }
            return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
        }
        return path.equals(requestPath);
    }

    public String rewritePath(String requestPath) {
        if (stripPrefix <= 0) {
            return requestPath;
        }

        String[] segments = requestPath.split("/");
        List<String> nonEmptySegments = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                nonEmptySegments.add(segment);
            }
        }

        if (stripPrefix >= nonEmptySegments.size()) {
            return "/";
        }

        StringBuilder rewritten = new StringBuilder("/");
        for (int i = stripPrefix; i < nonEmptySegments.size(); i++) {
            rewritten.append(nonEmptySegments.get(i));
            if (i < nonEmptySegments.size() - 1) {
                rewritten.append('/');
            }
        }
        return rewritten.toString();
    }
}
