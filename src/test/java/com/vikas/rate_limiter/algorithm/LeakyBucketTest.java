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
public class LeakyBucketTest {

    @Mock
    private StringRedisTemplate template;

    private LeakyBucketRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new LeakyBucketRateLimitAlgorithm(template);
    }

    private Map<String, Integer> createParams(int capacity, int flowRate) {
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("flowRate", flowRate);
        return params;
    }

    @Test
    void shouldAllowRequestWhenQueueNotFull() {
        String key = "user:192.168.1.1";
        Map<String, Integer> params = createParams(10, 2);

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
        assertEquals(1L, result.get(1));
    }

    @Test
    void shouldRejectRequestWhenQueueIsFull() {
        String key = "user:192.168.1.2";
        Map<String, Integer> params = createParams(5, 1);

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
    void shouldDrainQueueBasedOnElapsedTime() {
        String key = "user:192.168.1.3";
        Map<String, Integer> params = createParams(20, 5);

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
        assertTrue(result.get(1) < 10);
    }

    @Test
    void shouldAcceptRequestsUpToCapacity() {
        String key = "user:192.168.1.4";
        Map<String, Integer> params = createParams(10, 2);

        when(template.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(
                        List.of(1L, 1L),
                        List.of(1L, 2L),
                        List.of(1L, 9L),
                        List.of(1L, 10L));

        for (int i = 0; i < 4; i++) {
            List<Long> result = algorithm.acceptRequest(key, params);
            assertEquals(1L, result.get(0));
            assertTrue(result.get(1) <= 10);
        }
    }

    @Test
    void shouldHandleZeroFlowRate() {
        String key = "user:192.168.1.5";
        Map<String, Integer> params = createParams(10, 0);

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
    void shouldHandleDifferentConfigurationsPerUser() {
        String key1 = "user:192.168.1.6";
        String key2 = "user:192.168.1.7";
        Map<String, Integer> params1 = createParams(10, 2);
        Map<String, Integer> params2 = createParams(20, 5);

        when(template.execute(
                any(RedisScript.class),
                eq(List.of(key1)),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 1L));
        when(template.execute(
                any(RedisScript.class),
                eq(List.of(key2)),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(List.of(1L, 1L));

        List<Long> result1 = algorithm.acceptRequest(key1, params1);
        List<Long> result2 = algorithm.acceptRequest(key2, params2);

        assertEquals(1L, result1.get(0));
        assertEquals(1L, result2.get(0));
    }
}
