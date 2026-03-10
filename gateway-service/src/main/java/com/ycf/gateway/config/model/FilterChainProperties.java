package com.ycf.gateway.config.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilterChainProperties {

    private final List<String> preFilters;
    private final List<String> postFilters;

    public FilterChainProperties(List<String> preFilters, List<String> postFilters) {
        this.preFilters = copy(preFilters);
        this.postFilters = copy(postFilters);
    }

    public List<String> getPreFilters() {
        return Collections.unmodifiableList(preFilters);
    }

    public List<String> getPostFilters() {
        return Collections.unmodifiableList(postFilters);
    }

    private List<String> copy(List<String> source) {
        if (source == null) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String item : source) {
            if (item != null && !item.isBlank()) {
                filtered.add(item.trim());
            }
        }
        return List.copyOf(filtered);
    }
}
