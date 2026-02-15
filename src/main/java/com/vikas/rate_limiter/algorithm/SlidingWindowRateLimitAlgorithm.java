package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;

import lombok.Data;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Data
public class SlidingWindowRateLimitAlgorithm {

    private final StringRedisTemplate template;
    private final RedisScript<Long> script = getScript();
    private final ConfigurationStoreService configStore;

    public synchronized boolean acceptRequest(String key) {
        RequestConfigDTO config = configStore.getConfigWithIP(key);
        int maxRequests = config.getParameters().get("maxRequests");
        return template.execute(
                this.script,
                List.of(key),
                "30000",
                Integer.toString(maxRequests),
                Long.toString(System.currentTimeMillis()),
                "600000") == 1L;
    }

    public RedisScript<Long> getScript() {
        DefaultRedisScript script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("sliding-window.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
