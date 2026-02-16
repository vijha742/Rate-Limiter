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

    public RateLimitAlgorithm getAlgoWithIp(String ip) {
        RequestConfigDTO reqConfig = configStore.getConfigWithIP(ip);
        if (reqConfig != null) {
            log.info("Request Configuration is {}", reqConfig);
            Map<String, Integer> parameters = reqConfig.getParameters();
            log.info("Got these parameters {}", parameters);
            Algorithm algo = reqConfig.getAlgo();
            // NOTE: Possible only after Java 14 options are ->(single line case) and
            // yield(multi-line case)
            // NOTE: It is discouraged to use yield when the case is single line and -> can
            // be used
            // WARN: Why is it the variable which even won't be used and created still is
            // giving
            // errors, if intialized somewhere else...
            return findAlgorithm(algo);
        } else {
            log.warn("Request Config was null {}", reqConfig);
            return findAlgorithm(this.props.getFallback().getAlgorithm());
        }
    }

    public RateLimitDecision evaluateRequest(String ip) {
        RateLimitDecision decision = new RateLimitDecision();
        RequestConfigDTO reqConfig = configStore.getConfigWithIP(ip);
        RateLimitAlgorithm algo = getAlgoWithIp(ip);
        List<Long> info = algo.acceptRequest(ip);
        if (info.get(0) == 1L) {
            decision.setAllowed(true);
        } else {
            decision.setAllowed(false);
        }
        if (reqConfig.getAlgo() == Algorithm.TOKEN_BUCKET
                || reqConfig.getAlgo() == Algorithm.LEAKY_BUCKET) {
            decision.setLimit(
                    reqConfig
                            .getParameters()
                            .get("capacity")); // capacity -> TB,LB maxRequests -> SW, FC
        } else {
            decision.setLimit(reqConfig.getParameters().get("maxRequests"));
        }
        decision.setRemaining(info.get(1).intValue());
        decision.setResetOn(System.currentTimeMillis() + this.props.getRedis().getTtl());
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
