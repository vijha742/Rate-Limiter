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
public class SlidingWindowRateLimitAlgorithmTest {

    @Mock
    private StringRedisTemplate template;

    private SlidingWindowRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new SlidingWindowRateLimitAlgorithm(template);
    }

    private Map<String, Integer> createParams(int maxRequests, int windowTime) {
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", maxRequests);
        params.put("windowTime", windowTime);
        return params;
    }

    @Test
    void shouldAllowRequestWithinLimit() {
        String key = "user:192.168.1.1";
        Map<String, Integer> params = createParams(10, 60);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 1L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(1L, result.get(0));
    }

    @Test
    void shouldRejectRequestWhenLimitExceeded() {
        String key = "user:192.168.1.2";
        Map<String, Integer> params = createParams(5, 60);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(0L, 5L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(0L, result.get(0));
    }

    @Test
    void shouldResetWindowAfterTimePasses() {
        String key = "user:192.168.1.3";
        Map<String, Integer> params = createParams(10, 60);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 0L));

        List<Long> result = algorithm.acceptRequest(key, params);

        assertEquals(1L, result.get(0));
    }
}
