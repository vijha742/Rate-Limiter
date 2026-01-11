package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.FixedCounterRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.LeakyBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.RateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.SlidingWindowRateLimitAlgorithm;
import com.vikas.rate_limiter.algorithm.TokenBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

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
                RateLimitAlgorithm algorithm = switch (algo) {
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
                        int max_capacity = parameters.get("Max Capacity");
                        int refill_rate = parameters.get("Refill Rate");
                        yield new TokenBucketRateLimitAlgorithm(refill_rate, max_capacity);
                    case Algorithm.LEAKY_BUCKET:
                        int process_rate = parameters.get("Processing Rate");
                        max_capacity = parameters.get("Max Capacity");
                        yield new LeakyBucketRateLimitAlgorithm(process_rate, max_capacity);
                    case Algorithm.FIXED_WINDOW:
                        int max_requests = parameters.get("Max Requests");
                        int time_window = parameters.get("Window Length");
                        yield new FixedCounterRateLimitAlgorithm(max_requests, time_window);
                    case Algorithm.SLIDING_WINDOW:
                        max_requests = parameters.get("Max Requests");
                        time_window = parameters.get("Window Length");
                        yield new SlidingWindowRateLimitAlgorithm(
                                max_requests, time_window);
                };
                this.configStore.setAlgoWithIP(ip, algorithm);
                return algorithm;
            } else {
                log.warn("Request Config was null {}", reqConfig);
                RateLimitAlgorithm algorithm = new FixedCounterRateLimitAlgorithm(5, 10);
                this.configStore.setAlgoWithIP(ip, algorithm);
                return algorithm;
            }
        } else {
            return some;
        }
    }

    public boolean allowRequest(String ip) {
        RateLimitAlgorithm algo = getAlgoWithIp(ip);
        log.info("Limiting algorithm received {}", algo);
        if (algo.acceptRequest())
            return true;
        return false;
    }
}
