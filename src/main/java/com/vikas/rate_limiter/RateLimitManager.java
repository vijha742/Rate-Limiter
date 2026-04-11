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
import com.vikas.rate_limiter.service.MongoConfigurationStoreService;
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
    private final MongoConfigurationStoreService dbService;
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
        RateLimitConfigEntity config =
                dbService.getUserConfigWithIpAndEndpointAndUserTier(ip, uri, "free");
        if (config != null) {
            algo = findAlgorithm(config.getAlgorithm());
            Map<String, Integer> parameters = config.getParameters();
            List<Long> info = algo.acceptRequest(ip, parameters);
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
            decision.setRemaining(max - info.get(1).intValue());
            if (config.getAlgorithm() == Algorithm.FIXED_WINDOW) {
                decision.setResetOn(info.get(2));

            } else {
                decision.setResetOn(System.currentTimeMillis() + 1000);
            }
        } else {
            algo = findAlgorithm(this.props.getFallback().getAlgorithm());
            List<Long> info = algo.acceptRequest(ip, props.getFallback().getParameters());
            if (info.get(0) == 1L) {
                decision.setAllowed(true);
            } else {
                decision.setAllowed(false);
            }
            int max = (Integer) props.getFallback().getParameters().get("capacity");
            decision.setLimit(max);
            decision.setRemaining(max - info.get(1).intValue());
            // TODO: Find the fix for the reset time for the fallback algorithm, as it is
            // not being
            // set properly
            decision.setResetOn(System.currentTimeMillis() + 1000);
        }
        return decision;
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
