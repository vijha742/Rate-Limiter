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
public class LeakyBucketRateLimitAlgorithm implements RateLimitAlgorithm {

        private final StringRedisTemplate template;
        private final RedisScript<Long> script = getScript();
        private final ConfigurationStoreService configStore;

        @Override
        public synchronized boolean acceptRequest(String key) {
                RequestConfigDTO config = configStore.getConfigWithIP(key);
                int capacity = config.getParameters().get("capacity");
                int flowRate = config.getParameters().get("flowRate");
                return template.execute(
                                this.script,
                                List.of(key),
                                Integer.toString(capacity),
                                Long.toString(System.currentTimeMillis()),
                                Integer.toString(flowRate),
                                "600000") == 1L;
        }

        @Override
        public RedisScript<Long> getScript() {
                DefaultRedisScript script = new DefaultRedisScript<>();
                script.setLocation(new ClassPathResource("leaky-bucket.lua"));
                script.setResultType(Long.class);
                return script;
        }
}
