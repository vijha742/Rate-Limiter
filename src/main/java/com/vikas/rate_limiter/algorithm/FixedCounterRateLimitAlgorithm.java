package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Data
@Component
public class FixedCounterRateLimitAlgorithm implements RateLimitAlgorithm {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script = getScript();
    private final ConfigurationStoreService configStore;

    @Override
    public synchronized boolean acceptRequest(String key) {
        RequestConfigDTO config = configStore.getConfigWithIP(key);
        int maxReq = config.getParameters().get("maxRequests");
        int windowSize = config.getParameters().get("windowSize");
        return redisTemplate.execute(
                this.script,
                List.of(key),
                Integer.toString(maxReq),
                Integer.toString(windowSize)) == 1L;
    };

    @Override
    public RedisScript<Long> getScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("fixed-counter.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
