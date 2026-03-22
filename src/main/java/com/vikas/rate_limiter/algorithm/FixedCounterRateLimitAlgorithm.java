package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Component
public class FixedCounterRateLimitAlgorithm implements RateLimitAlgorithm {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script = getScript();
    private final ConfigurationStoreService configStore;

    @Override
    public synchronized List<Long> acceptRequest(String key, Map<String, Integer> parameters) {
        // RequestConfigDTO config = configStore.getConfigWithIP(key);
        int maxReq = parameters.get("maxRequests");
        int windowSize = parameters.get("windowSize");
        List<Long> res = redisTemplate.execute(
                this.script,
                List.of(key),
                Integer.toString(maxReq),
                Integer.toString(windowSize));
        return res;
    };

    @Override
    public RedisScript<List> getScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/fixed-counter.lua"));
        script.setResultType(List.class);
        return script;
    }
}
