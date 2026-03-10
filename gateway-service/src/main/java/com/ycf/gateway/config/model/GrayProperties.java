package com.ycf.gateway.config.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GrayProperties {

    private final boolean enabled;
    private final List<GrayRule> rules;

    public GrayProperties(boolean enabled, List<GrayRule> rules) {
        this.enabled = enabled;
        List<GrayRule> copied = new ArrayList<>();
        if (rules != null) {
            copied.addAll(rules);
        }
        copied.sort(Comparator.comparingInt(GrayRule::getPriority).reversed());
        this.rules = List.copyOf(copied);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<GrayRule> getRules() {
        return Collections.unmodifiableList(rules);
    }
}
