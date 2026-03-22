package com.vikas.rate_limiter.algorithm;

import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Map;

public interface RateLimitAlgorithm { // public type name

    List<Long> acceptRequest(String key, Map<String, Integer> parameters);

    RedisScript<List> getScript();
}
