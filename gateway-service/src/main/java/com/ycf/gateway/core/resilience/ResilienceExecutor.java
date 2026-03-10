package com.ycf.gateway.core.resilience;

import com.ycf.gateway.config.model.ResilienceProperties;
import com.ycf.gateway.config.model.ResilienceRule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

public class ResilienceExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ResilienceExecutor.class);

    private final ResilienceProperties resilienceProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final ScheduledExecutorService retryScheduler;

    public ResilienceExecutor(ResilienceProperties resilienceProperties) {
        this.resilienceProperties = resilienceProperties;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.retryScheduler = Executors.newScheduledThreadPool(2);
    }

    public <T> CompletableFuture<T> execute(String routeId,
                                            String serviceId,
                                            Supplier<CompletableFuture<T>> action,
                                            Supplier<T> fallback) {
        if (!resilienceProperties.isEnabled()) {
            return action.get();
        }

        String key = key(routeId, serviceId);
        ResilienceRule rule = resilienceProperties.ruleForService(serviceId);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                key,
                buildCircuitBreakerConfig(rule)
        );
        Retry retry = retryRegistry.retry(key, buildRetryConfig(rule));

        Supplier<CompletionStage<T>> supplier = () -> action.get();
        Supplier<CompletionStage<T>> circuitProtected = CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
        Supplier<CompletionStage<T>> retryProtected = Retry.decorateCompletionStage(retry, retryScheduler, circuitProtected);

        return retryProtected.get().toCompletableFuture().exceptionally(ex -> {
            log.warn("resilience fallback triggered, routeId={}, serviceId={}, cause={}",
                    routeId, serviceId, ex.toString());
            return fallback.get();
        });
    }

    @Override
    public void close() {
        retryScheduler.shutdownNow();
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(ResilienceRule rule) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(rule.getFailureRateThreshold())
                .slidingWindowSize(rule.getSlidingWindowSize())
                .minimumNumberOfCalls(rule.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(rule.getWaitDurationInOpenStateMs()))
                .build();
    }

    private RetryConfig buildRetryConfig(ResilienceRule rule) {
        return RetryConfig.custom()
                .maxAttempts(rule.getRetryAttempts())
                .waitDuration(Duration.ofMillis(rule.getRetryWaitMs()))
                .failAfterMaxAttempts(true)
                .build();
    }

    private String key(String routeId, String serviceId) {
        String safeRoute = routeId == null ? "unknown-route" : routeId;
        String safeService = serviceId == null ? "unknown-service" : serviceId;
        return safeRoute + "::" + safeService;
    }
}
