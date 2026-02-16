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
        private final RedisScript<List> script = getScript();
        private final ConfigurationStoreService configStore;

        @Override
        public synchronized List acceptRequest(String key) {
                RequestConfigDTO config = configStore.getConfigWithIP(key);
                int capacity = config.getParameters().get("capacity");
                int flowRate = config.getParameters().get("flowRate");
                List<Long> res = template.execute(
                                this.script,
                                List.of(key),
                                Integer.toString(capacity),
                                Long.toString(System.currentTimeMillis()),
                                Integer.toString(flowRate),
                                "600000");
                return res;
        }

        @Override
        public RedisScript<List> getScript() {
                DefaultRedisScript script = new DefaultRedisScript<>();
                script.setLocation(new ClassPathResource("leaky-bucket.lua"));
                script.setResultType(List.class);
                return script;
        }
}
