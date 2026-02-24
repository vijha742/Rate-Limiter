package com.vikas.rate_limiter.utils;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

	private EndPoints endpoints;
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

	public static class EndPoints {
		private String path;
		private Properties properties;
		private Redis redis;

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public Properties getProperties() {
			return properties;
		}

		public void setProperties(Properties properties) {
			this.properties = properties;
		}

		public Redis getRedis() {
			return redis;
		}

		public void setRedis(Redis redis) {
			this.redis = redis;
		}
	}

	public static class Properties {
		private Fallback fallback;
		private Limits limits;

		public Fallback getFallback() {
			return fallback;
		}

		public void setFallback(Fallback fallback) {
			this.fallback = fallback;
		}

		public Limits getLimits() {
			return limits;
		}

		public void setLimits(Limits limits) {
			this.limits = limits;
		}
	}

	public static class Fallback {
		private Algorithm algorithm;

		public Algorithm getAlgorithm() {
			return algorithm;
		}

		public void setAlgorithm(Algorithm algorithm) {
			this.algorithm = algorithm;
		}
	}

	public static class Limits {
		private int defaultCapacity;
		private int refillRate;

		public int getDefaultCapacity() {
			return defaultCapacity;
		}

		public void setDefaultCapacity(int defaultCapacity) {
			this.defaultCapacity = defaultCapacity;
		}

		public int getRefillRate() {
			return refillRate;
		}

		public void setRefillRate(int refillRate) {
			this.refillRate = refillRate;
		}
	}

	public static class Redis {
		private long ttl;
		private String keyPrefix;

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

	public EndPoints getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(EndPoints endpoints) {
		this.endpoints = endpoints;
	}

	public Security getSecurity() {
		return security;
	}

	public void setSecurity(Security security) {
		this.security = security;
	}

	// Convenience methods to access nested properties
	public Fallback getFallback() {
		return endpoints != null && endpoints.getProperties() != null 
			? endpoints.getProperties().getFallback() 
			: null;
	}

	public Limits getLimits() {
		return endpoints != null && endpoints.getProperties() != null 
			? endpoints.getProperties().getLimits() 
			: null;
	}

	public Redis getRedis() {
		return endpoints != null ? endpoints.getRedis() : null;
	}
}
