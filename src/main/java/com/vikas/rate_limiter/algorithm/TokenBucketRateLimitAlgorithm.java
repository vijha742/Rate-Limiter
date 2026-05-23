package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    public TokenBucketRateLimitAlgorithm(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public synchronized List<Long> acceptRequest(String key, Map<String, Integer> parameters) {
        int capacity, requested = 1;
        int refillRate;
        if (parameters != null) {
            capacity = parameters.get("capacity");
            refillRate = parameters.get("refillRate");
        } else {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        List<Long> res = template.execute(
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
