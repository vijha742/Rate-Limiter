package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.RateLimitAlgorithm;

import lombok.Data;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Data
@Component
public class RateLimitManager {

	private ConcurrentHashMap<String, RateLimitAlgorithm> user_algo_map;
}
