package com.ycf.gateway.config.model;

public enum LoadBalanceType {
    ROUND_ROBIN,
    RANDOM;

    public static LoadBalanceType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ROUND_ROBIN;
        }
        for (LoadBalanceType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return ROUND_ROBIN;
    }
}
