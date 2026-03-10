package com.ycf.gateway.config.model;

public class NacosRegisterProperties {

    private final boolean enabled;
    private final String groupName;

    public NacosRegisterProperties(boolean enabled, String groupName) {
        this.enabled = enabled;
        this.groupName = groupName == null || groupName.isBlank() ? "DEFAULT_GROUP" : groupName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getGroupName() {
        return groupName;
    }
}
