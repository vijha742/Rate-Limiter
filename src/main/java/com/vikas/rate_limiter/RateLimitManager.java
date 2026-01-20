package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.FixedCounterRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.LeakyBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.RateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.SlidingWindowRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.TokenBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RateLimitDecision;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

@Slf4j
@Data
@Component
public class RateLimitManager {

    private final ConfigurationStoreService configStore;

    public RateLimitAlgorithm getAlgoWithIp(String ip) {
        RateLimitAlgorithm some = configStore.getAlgoWithIP(ip);
        if (some == null) {
            RequestConfigDTO reqConfig = configStore.getConfigWithIP(ip);
            if (reqConfig != null) {
                log.info("Request Configuration is {}", reqConfig);
                Map<String, Integer> parameters = reqConfig.getParameters();
                log.info("Got these parameters {}", parameters);
                Algorithm algo = reqConfig.getAlgo();
                RateLimitAlgorithm algorithm =
                        switch (algo) {
                            // NOTE: Possible only after Java 14 options are ->(single line case)
                            // and
                            // yield(multi-line case)
                            // NOTE: It is discouraged to use yield when the case is single line and
                            // ->
                            // can
                            // be used
                            // WARN: Why is it the variable which even won't be used and created
                            // still
                            // is
                            // giving errors, if intialized somewhere else...
                            case Algorithm.TOKEN_BUCKET:
                                int maxCapacity = parameters.get("Max Capacity");
                                int refillRate = parameters.get("Refill Rate");
                                yield new TokenBucketRateLimitAlgorithm(
                                        refillRate, maxCapacity, Clock.systemDefaultZone());
                            case Algorithm.LEAKY_BUCKET:
                                int processRate = parameters.get("Processing Rate");
                                int maxLimit = parameters.get("Max Capacity");
                                yield new LeakyBucketRateLimitAlgorithm(
                                        processRate, maxLimit, Clock.systemDefaultZone());
                            case Algorithm.FIXED_WINDOW:
                                int maxRequests = parameters.get("Max Requests");
                                int timeWindow = parameters.get("Window Length");
                                yield new FixedCounterRateLimitAlgorithm(
                                        maxRequests, timeWindow, Clock.systemDefaultZone());
                            case Algorithm.SLIDING_WINDOW:
                                maxRequests = parameters.get("Max Requests");
                                timeWindow = parameters.get("Window Length");
                                yield new SlidingWindowRateLimitAlgorithm(
                                        Clock.systemDefaultZone(), maxRequests, timeWindow);
                        };
                this.configStore.setAlgoWithIP(ip, algorithm);
                return algorithm;
            } else {
                log.warn("Request Config was null {}", reqConfig);
                RateLimitAlgorithm algorithm =
                        new FixedCounterRateLimitAlgorithm(5, 10, Clock.systemDefaultZone());
                this.configStore.setAlgoWithIP(ip, algorithm);
                return algorithm;
            }
        } else {
            return some;
        }
    }

    // HACK: Start from here...
    public RateLimitDecision evaluateRequest(String ip) {
        RateLimitDecision decision = new RateLimitDecision();
        RateLimitAlgorithm algo = getAlgoWithIp(ip);
        decision.setAllowed(algo.acceptRequest());
        decision.setLimit(algo.getLimit());
        decision.setRemaining(algo.getRemainingRequests());
        decision.setResetOn(algo.resetTime());
        return decision;
    }
}
