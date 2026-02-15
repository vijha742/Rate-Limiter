package com.vikas.rate_limiter.algorithm;

import org.springframework.data.redis.core.script.RedisScript;

public interface RateLimitAlgorithm { // public type name

    boolean acceptRequest(String key);

    RedisScript<Long> getScript();
}
