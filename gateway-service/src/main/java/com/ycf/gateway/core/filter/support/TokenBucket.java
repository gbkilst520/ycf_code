package com.ycf.gateway.core.filter.support;

public class TokenBucket {

    private final double ratePerSecond;
    private final int capacity;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(double ratePerSecond, int capacity) {
        this.ratePerSecond = ratePerSecond;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }

        double refillTokens = (elapsed / 1_000_000_000.0d) * ratePerSecond;
        if (refillTokens > 0) {
            tokens = Math.min(capacity, tokens + refillTokens);
            lastRefillNanos = now;
        }
    }
}
