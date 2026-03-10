package com.ycf.gateway.config.model;

public class RateLimitRule {

    private final double rate;
    private final int capacity;

    public RateLimitRule(double rate, int capacity) {
        this.rate = rate <= 0 ? 0 : rate;
        this.capacity = capacity <= 0 ? (int) Math.ceil(this.rate) : capacity;
    }

    public double getRate() {
        return rate;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isEnabled() {
        return rate > 0;
    }
}
