package com.vikas.rate_limiter.algorithm;

import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

public interface RateLimitAlgorithm { // public type name

    List<Long> acceptRequest(String key);

    RedisScript<List> getScript();
}
