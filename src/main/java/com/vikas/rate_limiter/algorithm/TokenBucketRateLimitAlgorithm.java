package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.utils.RateLimiterProperties;

import lombok.Data;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

// NOTE: Used @component so that it becomes a Singleton and is initialized at the
// application
// startup. Without this application was being triggered at the RateFilter level
// and was
// stateless as it was being triggered for each request...This was causing
// unexpected
// behaviour while testing...
// But the question is why it wasn't happening for FixedCoubnter and sliding
// window algorithm...They
// were working as usual...
@Data
@Component
public class TokenBucketRateLimitAlgorithm implements RateLimitAlgorithm {

    private final StringRedisTemplate template;
    private final RedisScript<List> script = getScript();
    private final ConfigurationStoreService configStore;
    private final RateLimiterProperties props;

    @Override
    public synchronized List<Long> acceptRequest(String key) {
        RequestConfigDTO config = configStore.getConfigWithIP(key);
        int capacity, refillRate, requested = 1;
        if (config != null) {
            capacity = config.getParameters().get("capacity");
            refillRate = config.getParameters().get("refillRate");
            // HACK: Instead of this setup weighted endpoints and utilize those...
        } else {
            capacity = props.getLimits().getDefaultCapacity();
            refillRate = props.getLimits().getRefillRate();
        }
        List<Long> res =
                template.execute(
                        script,
                        List.of(key),
                        Integer.toString(capacity),
                        Integer.toString(refillRate),
                        Long.toString(System.currentTimeMillis()),
                        Integer.toString(requested),
                        "600000");
        return res;
    }

    @Override
    public RedisScript<List> getScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token-window.lua"));
        script.setResultType(List.class);
        return script;
    }
}
