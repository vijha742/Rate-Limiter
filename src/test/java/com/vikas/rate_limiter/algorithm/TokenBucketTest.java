package com.vikas.rate_limiter.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class TokenBucketTest {

    @Mock
    private StringRedisTemplate template;

    private TokenBucketRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketRateLimitAlgorithm(template);
    }

    private Map<String, Integer> createParams(int capacity, int refillRate) {
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("refillRate", refillRate);
        return params;
    }

    @Test
    void shouldAllowRequestWhenTokensAvailable() {
        String key = "user:192.168.1.1";
        Map<String, Integer> params = createParams(10, 2);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 1L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(1L, result.get(0));
        assertEquals(1L, result.get(1));
    }

    @Test
    void shouldRejectRequestWhenNoTokens() {
        String key = "user:192.168.1.2";
        Map<String, Integer> params = createParams(5, 1);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(0L, 0L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(0L, result.get(0));
    }

    @Test
    void shouldRefillTokensOverTime() {
        String key = "user:192.168.1.3";
        Map<String, Integer> params = createParams(20, 10);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 15L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(1L, result.get(0));
        assertTrue(result.get(1) > 0);
    }
}
