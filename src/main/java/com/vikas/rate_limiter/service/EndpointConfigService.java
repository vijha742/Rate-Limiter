package com.vikas.rate_limiter.service;

import com.vikas.rate_limiter.utils.RateLimiterProperties;
import com.vikas.rate_limiter.utils.RateLimiterProperties.EndpointConfig;

import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Service
public class EndpointConfigService {

    private List<EndpointConfig> endpointConfigs;
    private final AntPathMatcher uriMatcher = new AntPathMatcher();

    public EndpointConfigService(RateLimiterProperties properties) {
        this.endpointConfigs = properties.getEndpoints();
    }

    public EndpointConfig getEndpointConfig(String uri) {
        for (EndpointConfig config : endpointConfigs) {
            if (uriMatcher.match(config.getPath(), uri)) {
                return config;
            }
        }
        return null;
    }
}
