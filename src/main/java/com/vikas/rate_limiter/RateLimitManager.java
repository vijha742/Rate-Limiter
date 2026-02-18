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

    public RateLimitDecision evaluateRequest(String ip) {
        RateLimitDecision decision = new RateLimitDecision();
        RequestConfigDTO reqConfig = configStore.getConfigWithIP(ip);
        RateLimitAlgorithm algo;
        if (reqConfig != null) {
            Map<String, Integer> parameters = reqConfig.getParameters();
            Algorithm algorithm = reqConfig.getAlgo();
            algo = findAlgorithm(algorithm);
            List<Long> info = algo.acceptRequest(ip);
            if (info.get(0) == 1L) {
                decision.setAllowed(true);
            } else {
                decision.setAllowed(false);
            }
            int max = 0;
            if (reqConfig.getAlgo() == Algorithm.TOKEN_BUCKET
                    || reqConfig.getAlgo() == Algorithm.LEAKY_BUCKET) {
                max = reqConfig.getParameters().get("capacity");
            } else {
                max = reqConfig.getParameters().get("maxRequests");
            }
            decision.setLimit(max);
            decision.setRemaining(max - info.get(1).intValue());
            if (reqConfig.getAlgo() == Algorithm.FIXED_WINDOW) {
                decision.setResetOn(info.get(2));

            } else {
                decision.setResetOn(System.currentTimeMillis() + 1000);
            }
        } else {
            algo = findAlgorithm(this.props.getFallback().getAlgorithm());
            List<Long> info = algo.acceptRequest(ip);
            if (info.get(0) == 1L) {
                decision.setAllowed(true);
            } else {
                decision.setAllowed(false);
            }
            int max = props.getLimits().getDefaultCapacity();
            decision.setLimit(max);
            decision.setRemaining(max - info.get(1).intValue());
            decision.setResetOn(System.currentTimeMillis() + 1000);
        }
        return decision;
    }

    public RateLimitAlgorithm findAlgorithm(RequestConfigDTO.Algorithm algorithm) {
        RateLimitAlgorithm algo = switch (algorithm) {
            case Algorithm.TOKEN_BUCKET -> this.tokenBucketAlgo;
            case Algorithm.FIXED_WINDOW -> this.fixedWindowAlgo;
            case Algorithm.LEAKY_BUCKET -> this.leakyBucketAlgo;
            case Algorithm.SLIDING_WINDOW -> this.slidingWindowAlgo;
            default -> null;
        };
        return algo;
    }
}
