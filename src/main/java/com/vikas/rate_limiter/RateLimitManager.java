package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.FixedCounterRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.RateLimitAlgorithm;

import lombok.Data;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Data
@Component
public class RateLimitManager {

    private ConcurrentHashMap<String, RateLimitAlgorithm> user_algo_map;

    public RateLimitAlgorithm getAlgoWithIp(String ip) {
        if (this.user_algo_map.containsKey(ip)) return this.user_algo_map.get(ip);
        else {
            this.user_algo_map.put(ip, new FixedCounterRateLimitAlgorithm(5, 10));
            return new FixedCounterRateLimitAlgorithm(5, 10);
        }
    }
}
