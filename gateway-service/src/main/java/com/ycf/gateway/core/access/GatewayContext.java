package com.ycf.gateway.core.access;

import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.model.ServiceDefinition;
import com.ycf.gateway.register.ServiceInstance;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

public class GatewayContext {

    private final FullHttpRequest request;
    private final String requestId;
    private final String path;
    private final String query;
    private final String clientIp;
    private final long requestStartNanos;
    private RouteDefinition routeDefinition;
    private ServiceDefinition serviceDefinition;
    private ServiceInstance selectedInstance;
    private String targetBaseUrl;
    private String resolvedServiceId;

    public GatewayContext(FullHttpRequest request, String clientIp, String requestId) {
        this.request = request;
        this.clientIp = clientIp;
        this.requestId = requestId;
        this.requestStartNanos = System.nanoTime();

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
        this.path = queryStringDecoder.path();
        this.query = extractQuery(request.uri());
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getUri() {
        return request.uri();
    }

    public HttpMethod getMethod() {
        return request.method();
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public String getClientIp() {
        return clientIp;
    }

    public long getRequestStartNanos() {
        return requestStartNanos;
    }

    public RouteDefinition getRouteDefinition() {
        return routeDefinition;
    }

    public void setRouteDefinition(RouteDefinition routeDefinition) {
        this.routeDefinition = routeDefinition;
    }

    public ServiceDefinition getServiceDefinition() {
        return serviceDefinition;
    }

    public void setServiceDefinition(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    public ServiceInstance getSelectedInstance() {
        return selectedInstance;
    }

    public void setSelectedInstance(ServiceInstance selectedInstance) {
        this.selectedInstance = selectedInstance;
    }

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public String getResolvedServiceId() {
        return resolvedServiceId;
    }

    public void setResolvedServiceId(String resolvedServiceId) {
        this.resolvedServiceId = resolvedServiceId;
    }

    private String extractQuery(String uri) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0 || queryIndex == uri.length() - 1) {
            return "";
        }
        return uri.substring(queryIndex + 1);
    }
}
