package com.ycf.gateway.config.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ResilienceProperties {

    private final boolean enabled;
    private final ResilienceRule defaultRule;
    private final Map<String, ResilienceRule> byService;
    private final ResilienceFallbackProperties fallback;

    public ResilienceProperties(boolean enabled,
                                ResilienceRule defaultRule,
                                Map<String, ResilienceRule> byService,
                                ResilienceFallbackProperties fallback) {
        this.enabled = enabled;
        this.defaultRule = defaultRule;
        this.byService = byService == null ? Map.of() : Map.copyOf(new HashMap<>(byService));
        this.fallback = fallback;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ResilienceRule getDefaultRule() {
        return defaultRule;
    }

    public Map<String, ResilienceRule> getByService() {
        return Collections.unmodifiableMap(byService);
    }

    public ResilienceFallbackProperties getFallback() {
        return fallback;
    }

    public ResilienceRule ruleForService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return defaultRule;
        }
        return byService.getOrDefault(serviceId, defaultRule);
    }
}
