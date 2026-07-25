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
import com.vikas.rate_limiter.service.EndpointConfigService;
import com.vikas.rate_limiter.utils.RateLimiterProperties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Component
public class RateLimitManager {

    private final EndpointConfigService endpointService;
    private final ConfigurationStoreService configStore;
    private final FixedCounterRateLimitAlgorithm fixedWindowAlgo;
    private final TokenBucketRateLimitAlgorithm tokenBucketAlgo;
    private final SlidingWindowRateLimitAlgorithm slidingWindowAlgo;
    private final LeakyBucketRateLimitAlgorithm leakyBucketAlgo;
    private final RateLimiterProperties props;

    // NOTE: Possible only after Java 14 options are ->(single line case) and
    // yield(multi-line case)
    // NOTE: It is discouraged to use yield when the case is single line and -> can
    // be used
    // WARN: Why is it the variable which even won't be used and created still is
    // giving
    // errors, if intialized somewhere else...

    public RateLimitDecision evaluateRequest(String ip, String uri) {
        RateLimitDecision decision = new RateLimitDecision();
        // TODO: Replace this with DB method...
        RateLimitAlgorithm algo;
        // TODO: Add in-memory store for frequently used Configs...
        RateLimitConfigEntity config =
                configStore.getUserConfigWithIpAndEndpointAndUserTier(ip, uri, "free").orElseNull();
        if (config != null) {
            algo = findAlgorithm(config.getAlgorithm());
            Map<String, Integer> parameters = config.getParameters();
            List<Long> info = algo.acceptRequest(buildKey(ip, uri), parameters);
            if (info.get(0) == 1L) {
                decision.setAllowed(true);
            } else {
                decision.setAllowed(false);
            }
            int max = 0;
            if (config.getAlgorithm() == Algorithm.TOKEN_BUCKET
                    || config.getAlgorithm() == Algorithm.LEAKY_BUCKET) {
                max = config.getParameters().get("capacity");
            } else {
                max = config.getParameters().get("maxRequests");
            }
            decision.setLimit(max);
            decision.setRemaining(computeRemaining(config.getAlgorithm(), max, info));
            decision.setResetOn(computeResetOn(config.getAlgorithm(), info));
        } else {
            algo = findAlgorithm(this.props.getFallback().getAlgorithm());
            List<Long> info =
                    algo.acceptRequest(buildKey(ip, uri), props.getFallback().getParameters());
            if (info.get(0) == 1L) {
                decision.setAllowed(true);
            } else {
                decision.setAllowed(false);
            }
            int max =
                    getLimitFromParameters(
                            props.getFallback().getAlgorithm(),
                            props.getFallback().getParameters());
            decision.setLimit(max);
            decision.setRemaining(computeRemaining(props.getFallback().getAlgorithm(), max, info));
            decision.setResetOn(computeResetOn(props.getFallback().getAlgorithm(), info));
        }
        return decision;
    }

    private String buildKey(String ip, String uri) {
        String prefix = "rate-limit";
        if (props != null
                && props.getRedis() != null
                && props.getRedis().getKeyPrefix() != null
                && !props.getRedis().getKeyPrefix().isBlank()) {
            prefix = props.getRedis().getKeyPrefix();
        }
        return String.format("%s:%s:%s", prefix, ip, uri);
    }

    private int getLimitFromParameters(Algorithm algorithm, Map<String, Integer> parameters) {
        if (parameters == null) {
            return 0;
        }
        if (algorithm == Algorithm.TOKEN_BUCKET || algorithm == Algorithm.LEAKY_BUCKET) {
            return parameters.getOrDefault("capacity", 0);
        }
        return parameters.getOrDefault("maxRequests", 0);
    }

    private int computeRemaining(Algorithm algorithm, int limit, List<Long> info) {
        if (info == null || info.size() < 2) {
            return 0;
        }
        long value = info.get(1) == null ? 0L : info.get(1);
        if (algorithm == Algorithm.FIXED_WINDOW) {
            return Math.max(0, limit - (int) value);
        }
        if (algorithm == Algorithm.SLIDING_WINDOW) {
            return Math.max(0, limit - (int) value);
        }
        return Math.max(0, (int) value);
    }

    private long computeResetOn(Algorithm algorithm, List<Long> info) {
        if (algorithm == Algorithm.FIXED_WINDOW && info != null && info.size() >= 3) {
            long ttlSeconds = info.get(2) == null ? 0L : info.get(2);
            return System.currentTimeMillis() + (ttlSeconds * 1000);
        }
        if (algorithm == Algorithm.SLIDING_WINDOW && info != null && info.size() >= 3) {
            long windowMs = info.get(2) == null ? 0L : info.get(2);
            return System.currentTimeMillis() + windowMs;
        }
        if ((algorithm == Algorithm.TOKEN_BUCKET || algorithm == Algorithm.LEAKY_BUCKET)
                && info != null
                && info.size() >= 3) {
            long ttlMs = info.get(2) == null ? 0L : info.get(2);
            return System.currentTimeMillis() + ttlMs;
        }
        return System.currentTimeMillis();
    }

    public RateLimitAlgorithm findAlgorithm(RequestConfigDTO.Algorithm algorithm) {
        RateLimitAlgorithm algo =
                switch (algorithm) {
                    case Algorithm.TOKEN_BUCKET -> this.tokenBucketAlgo;
                    case Algorithm.FIXED_WINDOW -> this.fixedWindowAlgo;
                    case Algorithm.LEAKY_BUCKET -> this.leakyBucketAlgo;
                    case Algorithm.SLIDING_WINDOW -> this.slidingWindowAlgo;
                    default -> null;
                };
        return algo;
    }
}
