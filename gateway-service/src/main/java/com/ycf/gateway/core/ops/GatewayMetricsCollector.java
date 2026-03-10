package com.ycf.gateway.core.ops;

import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.core.access.GatewayContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.TimeUnit;

public class GatewayMetricsCollector implements AutoCloseable {

    private final PrometheusMeterRegistry meterRegistry;

    public GatewayMetricsCollector() {
        this(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    GatewayMetricsCollector(PrometheusMeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(GatewayContext context, int statusCode, long durationNanos, boolean internalEndpoint) {
        RouteDefinition route = context.getRouteDefinition();
        String routeId = route == null ? "UNMATCHED" : safe(route.getId(), "UNNAMED");
        String serviceId = safe(context.getResolvedServiceId(), "UNKNOWN");
        String method = safe(context.getMethod().name(), "UNKNOWN");
        String outcome = statusCode >= 500 ? "SERVER_ERROR" : statusCode >= 400 ? "CLIENT_ERROR" : "SUCCESS";

        Counter.builder("gateway_requests_total")
                .description("Total requests handled by gateway")
                .tag("method", method)
                .tag("route", routeId)
                .tag("service", serviceId)
                .tag("outcome", outcome)
                .tag("internal", Boolean.toString(internalEndpoint))
                .register(meterRegistry)
                .increment();

        Timer.builder("gateway_request_latency_ms")
                .description("Gateway end-to-end request latency")
                .publishPercentiles(0.5, 0.9, 0.99)
                .tag("method", method)
                .tag("route", routeId)
                .tag("service", serviceId)
                .tag("status", Integer.toString(statusCode))
                .tag("internal", Boolean.toString(internalEndpoint))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        if (statusCode >= 400) {
            Counter.builder("gateway_error_total")
                    .description("Total error responses from gateway")
                    .tag("method", method)
                    .tag("route", routeId)
                    .tag("service", serviceId)
                    .tag("status", Integer.toString(statusCode))
                    .tag("internal", Boolean.toString(internalEndpoint))
                    .register(meterRegistry)
                    .increment();
        }
    }

    public String scrape() {
        return meterRegistry.scrape();
    }

    public MeterRegistry meterRegistry() {
        return meterRegistry;
    }

    @Override
    public void close() {
        meterRegistry.close();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
