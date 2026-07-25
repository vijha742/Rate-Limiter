package com.vikas.rate_limiter.utils;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimiterProperties {

    private Fallback fallback;
    private List<EndpointConfig> endpoints;
    private Redis redis;
    private Security security;

    public static class Security {
        private boolean enabled;
        private List<String> trustedProxies;
        private boolean logSuspicious;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        public boolean isLogSuspicious() {
            return logSuspicious;
        }

        public void setLogSuspicious(boolean logSuspicious) {
            this.logSuspicious = logSuspicious;
        }
    }

    public static class EndpointConfig {
        private String path;
        private Algorithm algorithm;
        private Map<String, Integer> parameters;
        private Map<String, UserTierConfig> userTiers;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Map<String, UserTierConfig> getUserTiers() {
            return userTiers;
        }

        public void setUserTiers(Map<String, UserTierConfig> userTiers) {
            this.userTiers = userTiers;
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public Map<String, Integer> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Integer> parameters) {
            this.parameters = parameters;
        }
    }

    public static class UserTierConfig {
        private Algorithm algorithm;
        private Map<String, Integer> parameters;

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public Map<String, Integer> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Integer> parameters) {
            this.parameters = parameters;
        }
    }

    public static class Fallback {
        private Algorithm algorithm;
        private Map<String, Integer> parameters;

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public Map<String, Integer> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Integer> parameters) {
            this.parameters = parameters;
        }
    }

    public static class Redis {
        private long ttl = 600000L;
        private String keyPrefix = "rate-limit";

        public long getTtl() {
            return ttl;
        }

        public void setTtl(long ttl) {
            this.ttl = ttl;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public List<EndpointConfig> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointConfig> endpoints) {
        this.endpoints = endpoints;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    // Convenience method to get endpoint configuration by path
    public List<EndpointConfig> getEndpointConfig() {
        return this.endpoints;
    }
}
