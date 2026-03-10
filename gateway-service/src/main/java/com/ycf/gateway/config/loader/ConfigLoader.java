package com.ycf.gateway.config.loader;

import com.ycf.common.enums.ResponseCode;
import com.ycf.common.exception.GatewayException;
import com.ycf.gateway.config.model.FilterChainProperties;
import com.ycf.gateway.config.model.FlowProperties;
import com.ycf.gateway.config.model.GatewayConfig;
import com.ycf.gateway.config.model.GrayProperties;
import com.ycf.gateway.config.model.GrayRule;
import com.ycf.gateway.config.model.LoadBalanceType;
import com.ycf.gateway.config.model.NacosConfigProperties;
import com.ycf.gateway.config.model.NacosProperties;
import com.ycf.gateway.config.model.NacosRegisterProperties;
import com.ycf.gateway.config.model.RateLimitRule;
import com.ycf.gateway.config.model.ResilienceFallbackProperties;
import com.ycf.gateway.config.model.ResilienceProperties;
import com.ycf.gateway.config.model.ResilienceRule;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.config.model.ServiceDefinition;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigLoader {

    public GatewayConfig load(String classpathResource) {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "config file not found in classpath: " + classpathResource);
            }

            Yaml yaml = newYaml();
            Object loaded = yaml.load(inputStream);
            if (!(loaded instanceof Map<?, ?> rootRaw)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid yaml format, expected root map");
            }

            Map<String, Object> rootMap = castToStringObjectMap(rootRaw, "root");
            Map<String, Object> gatewayMap = getRequiredMap(rootMap, "gateway");

            int port = getInt(gatewayMap, "port", 8080);
            NacosProperties nacosProperties = parseNacosProperties(getOptionalMap(gatewayMap, "nacos"));
            List<ServiceDefinition> services = parseServices(getOptionalList(gatewayMap, "services"));
            List<RouteDefinition> routes = parseRoutes(getRequiredList(gatewayMap, "routes"));
            FilterChainProperties filterChainProperties = parseFilterChainProperties(getOptionalMap(gatewayMap, "filterChain"));
            FlowProperties flowProperties = parseFlowProperties(getOptionalMap(gatewayMap, "flow"));
            ResilienceProperties resilienceProperties = parseResilienceProperties(getOptionalMap(gatewayMap, "resilience"));
            GrayProperties grayProperties = parseGrayProperties(getOptionalMap(gatewayMap, "gray"));

            validateRoutes(routes);
            validateServicesAndRoutes(services, routes, nacosProperties.getRegister().isEnabled());

            return new GatewayConfig(
                    port,
                    routes,
                    services,
                    nacosProperties,
                    filterChainProperties,
                    flowProperties,
                    resilienceProperties,
                    grayProperties
            );
        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException(ResponseCode.BAD_REQUEST,
                    "failed to load gateway config: " + ex.getMessage(), ex);
        }
    }

    public List<RouteDefinition> loadRoutesFromText(String rawYamlText) {
        if (rawYamlText == null || rawYamlText.isBlank()) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "routes config text is empty");
        }

        try {
            Object loaded = newYaml().load(rawYamlText);
            if (loaded == null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "routes config text is empty");
            }

            List<RouteDefinition> routes;
            if (loaded instanceof List<?> routeListRaw) {
                routes = parseRoutes(new ArrayList<>(routeListRaw));
            } else if (loaded instanceof Map<?, ?> rootRaw) {
                Map<String, Object> rootMap = castToStringObjectMap(rootRaw, "route-root");
                routes = extractRoutesFromMap(rootMap);
            } else {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "invalid routes config format, expected map or list");
            }

            validateRoutes(routes);
            return routes;
        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException(ResponseCode.BAD_REQUEST,
                    "failed to parse dynamic routes: " + ex.getMessage(), ex);
        }
    }

    private List<RouteDefinition> extractRoutesFromMap(Map<String, Object> rootMap) {
        if (rootMap.containsKey("routes")) {
            return parseRoutes(getRequiredList(rootMap, "routes"));
        }
        Map<String, Object> gatewayMap = getOptionalMap(rootMap, "gateway");
        if (gatewayMap != null) {
            return parseRoutes(getRequiredList(gatewayMap, "routes"));
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST,
                "dynamic routes config should contain routes or gateway.routes");
    }

    private FilterChainProperties parseFilterChainProperties(Map<String, Object> filterChainMap) {
        List<String> defaultPre = List.of("gray", "flow");
        List<String> defaultPost = List.of("responseHeader", "accessLog");
        if (filterChainMap == null) {
            return new FilterChainProperties(defaultPre, defaultPost);
        }

        List<String> pre = parseStringList(filterChainMap.get("pre"), defaultPre);
        List<String> post = parseStringList(filterChainMap.get("post"), defaultPost);
        return new FilterChainProperties(pre, post);
    }

    private FlowProperties parseFlowProperties(Map<String, Object> flowMap) {
        if (flowMap == null) {
            return new FlowProperties(false, null, Map.of(), Map.of());
        }

        boolean enabled = getBoolean(flowMap, "enabled", false);
        RateLimitRule globalRule = parseRateLimitRule(flowMap.get("global"));
        Map<String, RateLimitRule> serviceRules = parseNamedRuleMap(getOptionalMap(flowMap, "service"));
        Map<String, RateLimitRule> ipRules = parseNamedRuleMap(getOptionalMap(flowMap, "ip"));
        return new FlowProperties(enabled, globalRule, serviceRules, ipRules);
    }

    private ResilienceProperties parseResilienceProperties(Map<String, Object> resilienceMap) {
        ResilienceRule defaultRule = new ResilienceRule(50f, 20, 10, 10_000L, 1, 0L);
        ResilienceFallbackProperties fallback = new ResilienceFallbackProperties(503, "Service temporarily unavailable");
        if (resilienceMap == null) {
            return new ResilienceProperties(false, defaultRule, Map.of(), fallback);
        }

        boolean enabled = getBoolean(resilienceMap, "enabled", false);
        ResilienceRule parsedDefaultRule = parseResilienceRule(getOptionalMap(resilienceMap, "default"), defaultRule);
        Map<String, ResilienceRule> byService = parseResilienceRuleMap(getOptionalMap(resilienceMap, "service"), parsedDefaultRule);

        Map<String, Object> fallbackMap = getOptionalMap(resilienceMap, "fallback");
        ResilienceFallbackProperties fallbackProperties;
        if (fallbackMap == null) {
            fallbackProperties = fallback;
        } else {
            fallbackProperties = new ResilienceFallbackProperties(
                    getInt(fallbackMap, "statusCode", 503),
                    getOptionalString(fallbackMap, "body")
            );
        }

        return new ResilienceProperties(enabled, parsedDefaultRule, byService, fallbackProperties);
    }

    private GrayProperties parseGrayProperties(Map<String, Object> grayMap) {
        if (grayMap == null) {
            return new GrayProperties(false, List.of());
        }

        boolean enabled = getBoolean(grayMap, "enabled", false);
        List<Object> rawRules = getOptionalList(grayMap, "rules");
        if (rawRules == null) {
            return new GrayProperties(enabled, List.of());
        }

        List<GrayRule> grayRules = new ArrayList<>();
        for (Object rawRule : rawRules) {
            if (!(rawRule instanceof Map<?, ?> ruleRawMap)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "gray rule must be map");
            }
            Map<String, Object> ruleMap = castToStringObjectMap(ruleRawMap, "gray-rule");

            String id = getRequiredString(ruleMap, "id");
            String sourceServiceId = getRequiredString(ruleMap, "sourceServiceId");
            String targetServiceId = getRequiredString(ruleMap, "targetServiceId");
            int priority = getInt(ruleMap, "priority", 0);

            String headerName;
            String headerValue;
            Map<String, Object> headerMap = getOptionalMap(ruleMap, "header");
            if (headerMap != null) {
                headerName = getOptionalString(headerMap, "name");
                headerValue = getOptionalString(headerMap, "value");
            } else {
                headerName = getOptionalString(ruleMap, "headerName");
                headerValue = getOptionalString(ruleMap, "headerValue");
            }

            String cookieName;
            String cookieValue;
            Map<String, Object> cookieMap = getOptionalMap(ruleMap, "cookie");
            if (cookieMap != null) {
                cookieName = getOptionalString(cookieMap, "name");
                cookieValue = getOptionalString(cookieMap, "value");
            } else {
                cookieName = getOptionalString(ruleMap, "cookieName");
                cookieValue = getOptionalString(ruleMap, "cookieValue");
            }

            Set<String> ips = parseIpSet(ruleMap);

            GrayRule grayRule = new GrayRule(
                    id,
                    sourceServiceId,
                    targetServiceId,
                    headerName,
                    headerValue,
                    cookieName,
                    cookieValue,
                    ips,
                    priority
            );
            if (!grayRule.hasAnyCondition()) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "gray rule must contain at least one condition(header/cookie/ip): " + grayRule.getId());
            }
            grayRules.add(grayRule);
        }

        return new GrayProperties(enabled, grayRules);
    }

    private Set<String> parseIpSet(Map<String, Object> ruleMap) {
        Set<String> ipSet = new LinkedHashSet<>();
        String singleIp = getOptionalString(ruleMap, "ip");
        if (singleIp != null) {
            ipSet.add(singleIp);
        }

        Object ipsObject = ruleMap.get("ips");
        if (ipsObject instanceof List<?> ipsRaw) {
            for (Object ipObj : ipsRaw) {
                if (ipObj instanceof String ipStr && !ipStr.isBlank()) {
                    ipSet.add(ipStr.trim());
                }
            }
        } else if (ipsObject instanceof String ipsStr && !ipsStr.isBlank()) {
            for (String part : ipsStr.split(",")) {
                if (!part.isBlank()) {
                    ipSet.add(part.trim());
                }
            }
        }
        return ipSet;
    }

    private ResilienceRule parseResilienceRuleMapItem(Map<String, Object> map, ResilienceRule fallbackRule) {
        return parseResilienceRule(map, fallbackRule);
    }

    private Map<String, ResilienceRule> parseResilienceRuleMap(Map<String, Object> map, ResilienceRule defaultRule) {
        if (map == null) {
            return Map.of();
        }

        Map<String, ResilienceRule> rules = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> rawRuleMap)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "resilience service rule must be map, service=" + entry.getKey());
            }
            Map<String, Object> serviceRuleMap = castToStringObjectMap(rawRuleMap, "resilience-service");
            rules.put(entry.getKey(), parseResilienceRuleMapItem(serviceRuleMap, defaultRule));
        }
        return rules;
    }

    private ResilienceRule parseResilienceRule(Map<String, Object> map, ResilienceRule fallback) {
        if (map == null) {
            return fallback;
        }
        return new ResilienceRule(
                getFloat(map, "failureRateThreshold", fallback.getFailureRateThreshold()),
                getInt(map, "slidingWindowSize", fallback.getSlidingWindowSize()),
                getInt(map, "minimumNumberOfCalls", fallback.getMinimumNumberOfCalls()),
                getLong(map, "waitDurationInOpenStateMs", fallback.getWaitDurationInOpenStateMs()),
                getInt(map, "retryAttempts", fallback.getRetryAttempts()),
                getLong(map, "retryWaitMs", fallback.getRetryWaitMs())
        );
    }

    private Map<String, RateLimitRule> parseNamedRuleMap(Map<String, Object> namedMap) {
        if (namedMap == null) {
            return Map.of();
        }

        Map<String, RateLimitRule> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : namedMap.entrySet()) {
            RateLimitRule rule = parseRateLimitRule(entry.getValue());
            if (rule != null && rule.isEnabled()) {
                result.put(entry.getKey(), rule);
            }
        }
        return result;
    }

    private RateLimitRule parseRateLimitRule(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Number num) {
            double rate = num.doubleValue();
            return new RateLimitRule(rate, (int) Math.ceil(rate));
        }

        if (rawValue instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            try {
                double rate = Double.parseDouble(str.trim());
                return new RateLimitRule(rate, (int) Math.ceil(rate));
            } catch (NumberFormatException ex) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid rate limit rule: " + str);
            }
        }

        if (rawValue instanceof Map<?, ?> rawMap) {
            Map<String, Object> ruleMap = castToStringObjectMap(rawMap, "rate-limit-rule");
            double rate = getDouble(ruleMap, "rate", 0);
            int capacity = getInt(ruleMap, "capacity", (int) Math.ceil(rate));
            RateLimitRule rule = new RateLimitRule(rate, capacity);
            return rule.isEnabled() ? rule : null;
        }

        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid rate limit rule type");
    }

    private NacosProperties parseNacosProperties(Map<String, Object> nacosMap) {
        if (nacosMap == null) {
            return new NacosProperties(
                    null,
                    null,
                    null,
                    null,
                    new NacosRegisterProperties(false, "DEFAULT_GROUP"),
                    new NacosConfigProperties(false, "gateway-routes.yaml", "DEFAULT_GROUP", 3000L)
            );
        }

        String serverAddr = getOptionalString(nacosMap, "serverAddr");
        String namespace = getOptionalString(nacosMap, "namespace");
        String username = getOptionalString(nacosMap, "username");
        String password = getOptionalString(nacosMap, "password");

        Map<String, Object> registerMap = getOptionalMap(nacosMap, "register");
        boolean registerEnabled = getBoolean(registerMap, "enabled", false);
        String registerGroup = registerMap == null ? "DEFAULT_GROUP"
                : getOptionalString(registerMap, "groupName");

        Map<String, Object> configMap = getOptionalMap(nacosMap, "config");
        boolean configEnabled = getBoolean(configMap, "enabled", false);
        String dataId = configMap == null ? "gateway-routes.yaml" : getOptionalString(configMap, "dataId");
        String group = configMap == null ? "DEFAULT_GROUP" : getOptionalString(configMap, "group");
        long timeoutMs = configMap == null ? 3000L : getLong(configMap, "timeoutMs", 3000L);

        NacosProperties nacosProperties = new NacosProperties(
                serverAddr,
                namespace,
                username,
                password,
                new NacosRegisterProperties(registerEnabled, registerGroup),
                new NacosConfigProperties(configEnabled, dataId, group, timeoutMs)
        );

        if ((registerEnabled || configEnabled) && !nacosProperties.hasServerAddress()) {
            throw new GatewayException(ResponseCode.BAD_REQUEST,
                    "nacos.serverAddr is required when nacos register/config enabled");
        }
        return nacosProperties;
    }

    private List<ServiceDefinition> parseServices(List<Object> serviceList) {
        if (serviceList == null) {
            return List.of();
        }
        List<ServiceDefinition> services = new ArrayList<>();
        for (Object item : serviceList) {
            if (!(item instanceof Map<?, ?> itemMapRaw)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "service item must be map");
            }
            Map<String, Object> itemMap = castToStringObjectMap(itemMapRaw, "service");
            String serviceId = getRequiredString(itemMap, "serviceId");
            String baseUrl = normalizeBaseUrl(getOptionalString(itemMap, "baseUrl"));
            services.add(new ServiceDefinition(serviceId, baseUrl));
        }
        return services;
    }

    private List<RouteDefinition> parseRoutes(List<Object> routeList) {
        List<RouteDefinition> routes = new ArrayList<>();
        for (Object item : routeList) {
            if (!(item instanceof Map<?, ?> itemMapRaw)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "route item must be map");
            }
            Map<String, Object> itemMap = castToStringObjectMap(itemMapRaw, "route");
            String routeId = getRequiredString(itemMap, "id");
            String path = getRequiredString(itemMap, "path");
            String serviceId = getRequiredString(itemMap, "serviceId");
            int stripPrefix = getInt(itemMap, "stripPrefix", 0);
            String loadBalanceRaw = firstNonBlank(
                    getOptionalString(itemMap, "loadBalance"),
                    getOptionalString(itemMap, "lb")
            );
            LoadBalanceType loadBalanceType = LoadBalanceType.fromString(loadBalanceRaw);
            routes.add(new RouteDefinition(routeId, path, serviceId, stripPrefix, loadBalanceType));
        }
        return routes;
    }

    private void validateRoutes(List<RouteDefinition> routes) {
        if (routes == null || routes.isEmpty()) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "routes can not be empty");
        }

        Set<String> routeIds = new HashSet<>();
        for (RouteDefinition route : routes) {
            if (!routeIds.add(route.getId())) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "duplicated route id: " + route.getId());
            }
        }
    }

    private void validateServicesAndRoutes(List<ServiceDefinition> services,
                                           List<RouteDefinition> routes,
                                           boolean registerEnabled) {
        Map<String, ServiceDefinition> serviceMap = new HashMap<>();
        for (ServiceDefinition service : services) {
            if (serviceMap.putIfAbsent(service.getServiceId(), service) != null) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "duplicated serviceId: " + service.getServiceId());
            }
        }

        if (registerEnabled) {
            return;
        }

        for (RouteDefinition route : routes) {
            ServiceDefinition serviceDefinition = serviceMap.get(route.getServiceId());
            if (serviceDefinition == null || !serviceDefinition.hasBaseUrl()) {
                throw new GatewayException(ResponseCode.BAD_REQUEST,
                        "route service missing static baseUrl while register center disabled: "
                                + route.getServiceId());
            }
        }
    }

    private List<String> parseStringList(Object rawListObj, List<String> defaultValue) {
        if (rawListObj == null) {
            return defaultValue;
        }
        if (!(rawListObj instanceof List<?> rawList)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid string list value");
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String str && !str.isBlank()) {
                result.add(str.trim());
            }
        }
        return result.isEmpty() ? defaultValue : List.copyOf(result);
    }

    private Yaml newYaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private Map<String, Object> castToStringObjectMap(Map<?, ?> rawMap, String fieldName) {
        Map<String, Object> casted = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, fieldName + " contains non-string key");
            }
            casted.put(key, entry.getValue());
        }
        return casted;
    }

    private Map<String, Object> getRequiredMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "missing required map: " + key);
        }
        return castToStringObjectMap(rawMap, key);
    }

    private Map<String, Object> getOptionalMap(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid map value for key: " + key);
        }
        return castToStringObjectMap(rawMap, key);
    }

    private List<Object> getRequiredList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> rawList)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "missing required list: " + key);
        }
        return new ArrayList<>(rawList);
    }

    private List<Object> getOptionalList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> rawList)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid list value for key: " + key);
        }
        return new ArrayList<>(rawList);
    }

    private String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String strValue) || strValue.isBlank()) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "missing required string: " + key);
        }
        return strValue;
    }

    private String getOptionalString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String strValue)) {
            throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid string value for key: " + key);
        }
        return strValue.isBlank() ? null : strValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer intValue) {
            return intValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String strValue) {
            try {
                return Integer.parseInt(strValue);
            } catch (NumberFormatException ex) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid int value for key: " + key);
            }
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid int value for key: " + key);
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String strValue) {
            try {
                return Long.parseLong(strValue);
            } catch (NumberFormatException ex) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid long value for key: " + key);
            }
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid long value for key: " + key);
    }

    private float getFloat(Map<String, Object> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Float floatValue) {
            return floatValue;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String strValue) {
            try {
                return Float.parseFloat(strValue);
            } catch (NumberFormatException ex) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid float value for key: " + key);
            }
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid float value for key: " + key);
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String strValue) {
            try {
                return Double.parseDouble(strValue);
            } catch (NumberFormatException ex) {
                throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid double value for key: " + key);
            }
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid double value for key: " + key);
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String strValue) {
            return Boolean.parseBoolean(strValue);
        }
        throw new GatewayException(ResponseCode.BAD_REQUEST, "invalid boolean value for key: " + key);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
