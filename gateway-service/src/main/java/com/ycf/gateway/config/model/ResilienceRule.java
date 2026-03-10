package com.ycf.gateway.config.model;

public class ResilienceRule {

    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final int minimumNumberOfCalls;
    private final long waitDurationInOpenStateMs;
    private final int retryAttempts;
    private final long retryWaitMs;

    public ResilienceRule(float failureRateThreshold,
                          int slidingWindowSize,
                          int minimumNumberOfCalls,
                          long waitDurationInOpenStateMs,
                          int retryAttempts,
                          long retryWaitMs) {
        this.failureRateThreshold = failureRateThreshold <= 0 ? 50f : failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize <= 0 ? 20 : slidingWindowSize;
        this.minimumNumberOfCalls = minimumNumberOfCalls <= 0 ? 10 : minimumNumberOfCalls;
        this.waitDurationInOpenStateMs = waitDurationInOpenStateMs <= 0 ? 10_000L : waitDurationInOpenStateMs;
        this.retryAttempts = retryAttempts <= 0 ? 1 : retryAttempts;
        this.retryWaitMs = retryWaitMs < 0 ? 0 : retryWaitMs;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public int getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public long getWaitDurationInOpenStateMs() {
        return waitDurationInOpenStateMs;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public long getRetryWaitMs() {
        return retryWaitMs;
    }
}
