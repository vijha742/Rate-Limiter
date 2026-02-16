package com.vikas.rate_limiter.utils;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limiter.properties")
public class RateLimiterProperties {

	private Fallback fallback;
	private Redis redis;
	private Limits limits;

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
}
