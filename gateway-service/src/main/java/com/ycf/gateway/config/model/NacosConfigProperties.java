package com.ycf.gateway.config.model;

public class NacosConfigProperties {

    private final boolean enabled;
    private final String dataId;
    private final String group;
    private final long timeoutMs;

    public NacosConfigProperties(boolean enabled, String dataId, String group, long timeoutMs) {
        this.enabled = enabled;
        this.dataId = dataId == null || dataId.isBlank() ? "gateway-routes.yaml" : dataId;
        this.group = group == null || group.isBlank() ? "DEFAULT_GROUP" : group;
        this.timeoutMs = timeoutMs <= 0 ? 3000L : timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDataId() {
        return dataId;
    }

    public String getGroup() {
        return group;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
