package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;

import lombok.Data;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
public class SlidingWindowRateLimitAlgorithm implements RateLimitAlgorithm {

    private final StringRedisTemplate template;
    private final RedisScript<List> script = getScript();
    private final ConfigurationStoreService configStore;

    @Override
    public synchronized List<Long> acceptRequest(String key) {
        RequestConfigDTO config = configStore.getConfigWithIP(key);
        int maxRequests = config.getParameters().get("maxRequests");
        List<Long> res = template.execute(
                this.script,
                List.of(key),
                "30000",
                Integer.toString(maxRequests),
                Long.toString(System.currentTimeMillis()),
                "600000");
        return res;
    }

    @Override
    public RedisScript<List> getScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/sliding-window.lua"));
        script.setResultType(List.class);
        return script;
    }
}
