package com.vikas.rate_limiter.utils;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rate-limiter.properties")
public class RateLimiterProperties {

	private Fallback fallback;
	private Redis redis;
	private Limits limits;
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

	public static class Fallback {
		private Algorithm algorithm;

		public Algorithm getAlgorithm() {
			return algorithm;
		}

		public void setAlgorithm(Algorithm algorithm) {
			this.algorithm = algorithm;
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

	public Fallback getFallback() {
		return fallback;
	}

	public void setFallback(Fallback fallback) {
		this.fallback = fallback;
	}

	public Redis getRedis() {
		return redis;
	}

	public void setRedis(Redis redis) {
		this.redis = redis;
	}

	public Limits getLimits() {
		return limits;
	}

	public void setLimits(Limits limits) {
		this.limits = limits;
	}

	public Security getSecurity() {
		return security;
	}

	public void setSecurity(Security security) {
		this.security = security;
	}
}
