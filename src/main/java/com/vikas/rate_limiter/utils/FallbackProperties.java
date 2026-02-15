package com.vikas.rate_limiter.utils;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limiter.properties.fallback")
public class FallbackProperties {

	private Algorithm algorithm;

	public Algorithm getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
	}
}
