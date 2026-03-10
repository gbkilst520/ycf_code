package com.ycf.gateway.config.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FlowProperties {

    private final boolean enabled;
    private final RateLimitRule globalRule;
    private final Map<String, RateLimitRule> serviceRules;
    private final Map<String, RateLimitRule> ipRules;

    public FlowProperties(boolean enabled,
                          RateLimitRule globalRule,
                          Map<String, RateLimitRule> serviceRules,
                          Map<String, RateLimitRule> ipRules) {
        this.enabled = enabled;
        this.globalRule = globalRule;
        this.serviceRules = serviceRules == null ? Map.of() : Map.copyOf(new HashMap<>(serviceRules));
        this.ipRules = ipRules == null ? Map.of() : Map.copyOf(new HashMap<>(ipRules));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RateLimitRule getGlobalRule() {
        return globalRule;
    }

    public Map<String, RateLimitRule> getServiceRules() {
        return Collections.unmodifiableMap(serviceRules);
    }

    public Map<String, RateLimitRule> getIpRules() {
        return Collections.unmodifiableMap(ipRules);
    }
}
