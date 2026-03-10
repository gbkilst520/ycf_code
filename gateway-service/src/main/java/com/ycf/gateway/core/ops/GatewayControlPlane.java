package com.ycf.gateway.core.ops;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.config.loader.ConfigLoader;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.model.ServiceDefinition;
import com.ycf.gateway.config.repository.RouteRepository;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.util.HttpResponseFactory;
import com.ycf.gateway.register.RegisterCenter;
import com.ycf.gateway.register.ServiceInstance;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GatewayControlPlane {

    private static final String PATH_METRICS = "/metrics/prometheus";
    private static final String PATH_ADMIN_INDEX = "/admin";
    private static final String PATH_ADMIN_ROUTES = "/admin/api/routes";
    private static final String PATH_ADMIN_SERVICES = "/admin/api/services";
    private static final String PATH_ADMIN_METRICS = "/admin/api/metrics/prometheus";
    private static final String PATH_ADMIN_DASHBOARD_CSS = "/admin/assets/dashboard.css";
    private static final String PATH_ADMIN_DASHBOARD_JS = "/admin/assets/dashboard.js";

    private static final String RESOURCE_DASHBOARD_HTML = "dashboard/index.html";
    private static final String RESOURCE_DASHBOARD_CSS = "dashboard/dashboard.css";
    private static final String RESOURCE_DASHBOARD_JS = "dashboard/dashboard.js";

    private final RouteRepository routeRepository;
    private final RegisterCenter registerCenter;
    private final ConfigLoader configLoader;
    private final GatewayMetricsCollector metricsCollector;
    private final Map<String, ServiceDefinition> staticServices = new LinkedHashMap<>();

    private final String dashboardHtml;
    private final String dashboardCss;
    private final String dashboardJs;

    public GatewayControlPlane(RouteRepository routeRepository,
                               RegisterCenter registerCenter,
                               List<ServiceDefinition> serviceDefinitions,
                               ConfigLoader configLoader,
                               GatewayMetricsCollector metricsCollector) {
        this.routeRepository = routeRepository;
        this.registerCenter = registerCenter;
        this.configLoader = configLoader;
        this.metricsCollector = metricsCollector;

        if (serviceDefinitions != null) {
            for (ServiceDefinition serviceDefinition : serviceDefinitions) {
                staticServices.put(serviceDefinition.getServiceId(), serviceDefinition);
            }
        }

        this.dashboardHtml = loadResourceText(RESOURCE_DASHBOARD_HTML);
        this.dashboardCss = loadResourceText(RESOURCE_DASHBOARD_CSS);
        this.dashboardJs = loadResourceText(RESOURCE_DASHBOARD_JS);
    }

    public boolean isControlPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return PATH_METRICS.equals(path)
                || PATH_ADMIN_INDEX.equals(path)
                || path.startsWith(PATH_ADMIN_INDEX + "/");
    }

    public FullHttpResponse handle(GatewayContext context) {
        String path = context.getPath();
        FullHttpRequest request = context.getRequest();

        if (PATH_METRICS.equals(path) || PATH_ADMIN_METRICS.equals(path)) {
            if (!HttpMethod.GET.equals(request.method())) {
                return HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "metrics endpoint only supports GET");
            }
            return prometheusResponse();
        }

        if (PATH_ADMIN_INDEX.equals(path) || (PATH_ADMIN_INDEX + "/").equals(path)) {
            if (!HttpMethod.GET.equals(request.method())) {
                return HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "admin page only supports GET");
            }
            return textResponse(HttpResponseStatus.OK, dashboardHtml, "text/html; charset=UTF-8");
        }

        if (PATH_ADMIN_DASHBOARD_CSS.equals(path)) {
            if (!HttpMethod.GET.equals(request.method())) {
                return HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "css endpoint only supports GET");
            }
            return textResponse(HttpResponseStatus.OK, dashboardCss, "text/css; charset=UTF-8");
        }

        if (PATH_ADMIN_DASHBOARD_JS.equals(path)) {
            if (!HttpMethod.GET.equals(request.method())) {
                return HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "js endpoint only supports GET");
            }
            return textResponse(HttpResponseStatus.OK, dashboardJs, "application/javascript; charset=UTF-8");
        }

        if (PATH_ADMIN_ROUTES.equals(path)) {
            if (HttpMethod.GET.equals(request.method())) {
                return textResponse(HttpResponseStatus.OK, routesJson(), "application/json; charset=UTF-8");
            }
            if (HttpMethod.POST.equals(request.method()) || HttpMethod.PUT.equals(request.method())) {
                return updateRoutes(request);
            }
            return HttpResponseFactory.error(ResponseCode.BAD_REQUEST,
                    "admin routes endpoint only supports GET/POST/PUT");
        }

        if (PATH_ADMIN_SERVICES.equals(path)) {
            if (!HttpMethod.GET.equals(request.method())) {
                return HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "admin services endpoint only supports GET");
            }
            return textResponse(HttpResponseStatus.OK, servicesJson(), "application/json; charset=UTF-8");
        }

        return HttpResponseFactory.error(ResponseCode.NOT_FOUND, "unknown admin endpoint: " + path);
    }

    private FullHttpResponse updateRoutes(FullHttpRequest request) {
        String rawPayload = new String(ByteBufUtil.getBytes(request.content()), CharsetUtil.UTF_8);
        if (rawPayload.isBlank()) {
            return HttpResponseFactory.error(ResponseCode.BAD_REQUEST,
                    "routes update payload is empty, expected yaml text");
        }

        List<RouteDefinition> routes;
        try {
            routes = configLoader.loadRoutesFromText(rawPayload);
            routeRepository.replaceAll(routes);
            registerCenter.watchServices(extractServiceIds(routes));
        } catch (Exception ex) {
            return HttpResponseFactory.error(ResponseCode.BAD_REQUEST,
                    "invalid route config: " + ex.getMessage());
        }

        return textResponse(
                HttpResponseStatus.OK,
                "{\"code\":0,\"message\":\"ok\",\"routeCount\":" + routes.size() + "}",
                "application/json; charset=UTF-8"
        );
    }

    private Set<String> extractServiceIds(List<RouteDefinition> routes) {
        Set<String> serviceIds = new LinkedHashSet<>();
        for (RouteDefinition route : routes) {
            serviceIds.add(route.getServiceId());
        }
        return serviceIds;
    }

    private String routesJson() {
        List<RouteDefinition> routes = routeRepository.getAllRoutes();
        StringBuilder builder = new StringBuilder();
        builder.append('{')
                .append("\"count\":").append(routes.size()).append(',')
                .append("\"routes\":[");
        for (int i = 0; i < routes.size(); i++) {
            RouteDefinition route = routes.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"id\":\"").append(jsonEscape(route.getId())).append("\",")
                    .append("\"path\":\"").append(jsonEscape(route.getPath())).append("\",")
                    .append("\"serviceId\":\"").append(jsonEscape(route.getServiceId())).append("\",")
                    .append("\"stripPrefix\":").append(route.getStripPrefix()).append(',')
                    .append("\"loadBalance\":\"").append(route.getLoadBalanceType()).append("\"")
                    .append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private String servicesJson() {
        Set<String> allServiceIds = new LinkedHashSet<>();
        allServiceIds.addAll(staticServices.keySet());
        allServiceIds.addAll(routeRepository.allServiceIds());
        allServiceIds.addAll(registerCenter.getWatchedServiceIds());

        List<String> serviceIds = new ArrayList<>(allServiceIds);
        StringBuilder builder = new StringBuilder();
        builder.append('{')
                .append("\"count\":").append(serviceIds.size()).append(',')
                .append("\"services\":[");

        for (int i = 0; i < serviceIds.size(); i++) {
            String serviceId = serviceIds.get(i);
            if (i > 0) {
                builder.append(',');
            }
            ServiceDefinition staticDefinition = staticServices.get(serviceId);
            List<ServiceInstance> instances = registerCenter.getInstances(serviceId);

            builder.append('{')
                    .append("\"serviceId\":\"").append(jsonEscape(serviceId)).append("\",")
                    .append("\"staticBaseUrl\":");
            if (staticDefinition == null || !staticDefinition.hasBaseUrl()) {
                builder.append("null");
            } else {
                builder.append('"').append(jsonEscape(staticDefinition.getBaseUrl())).append('"');
            }
            builder.append(',')
                    .append("\"instanceCount\":").append(instances.size()).append(',')
                    .append("\"instances\":[");

            for (int j = 0; j < instances.size(); j++) {
                ServiceInstance instance = instances.get(j);
                if (j > 0) {
                    builder.append(',');
                }
                builder.append('{')
                        .append("\"host\":\"").append(jsonEscape(instance.getHost())).append("\",")
                        .append("\"port\":").append(instance.getPort()).append(',')
                        .append("\"baseUrl\":\"").append(jsonEscape(instance.baseUrl())).append("\",")
                        .append("\"metadata\":").append(metadataJson(instance.getMetadata()))
                        .append('}');
            }
            builder.append("]}");
        }

        builder.append("]}");
        return builder.toString();
    }

    private String metadataJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(jsonEscape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(jsonEscape(entry.getValue())).append('"');
            i++;
        }
        builder.append('}');
        return builder.toString();
    }

    private FullHttpResponse prometheusResponse() {
        return textResponse(
                HttpResponseStatus.OK,
                metricsCollector.scrape(),
                "text/plain; version=0.0.4; charset=utf-8"
        );
    }

    private FullHttpResponse textResponse(HttpResponseStatus status, String body, String contentType) {
        FullHttpResponse response = HttpResponseFactory.text(status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        return response;
    }

    private String loadResourceText(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("dashboard resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), CharsetUtil.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load dashboard resource: " + resourcePath, ex);
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
