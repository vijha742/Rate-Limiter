package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.utils.RateLimiterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimitAlgorithmTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ConfigurationStoreService configStore;

    @Mock
    private RateLimiterProperties properties;

    @Mock
    private RequestConfigDTO requestConfigDTO;

    private SlidingWindowRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new SlidingWindowRateLimitAlgorithm(redisTemplate, configStore, properties);
    }

    @Test
    void acceptRequest_FirstRequestWithinLimit_ReturnsAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("10"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0));
        assertEquals(0L, result.get(1));
    }

    @Test
    void acceptRequest_RequestAtLimit_ReturnsAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("5"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 4L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(1L, result.get(0));
    }

    @Test
    void acceptRequest_RequestExceedsLimit_ReturnsNotAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("5"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(0L, 5L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(0L, result.get(0));
        assertEquals(5L, result.get(1));
    }

    @Test
    void acceptRequest_MultipleRequestsWithinWindow_GraduallyBlocks() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 3);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("3"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L))
                .thenReturn(Arrays.asList(1L, 1L))
                .thenReturn(Arrays.asList(1L, 2L))
                .thenReturn(Arrays.asList(0L, 3L));

        List<Long> result1 = algorithm.acceptRequest(key);
        List<Long> result2 = algorithm.acceptRequest(key);
        List<Long> result3 = algorithm.acceptRequest(key);
        List<Long> result4 = algorithm.acceptRequest(key);

        assertEquals(1L, result1.get(0));
        assertEquals(1L, result2.get(0));
        assertEquals(1L, result3.get(0));
        assertEquals(0L, result4.get(0));
    }

    @Test
    void acceptRequest_NullConfig_ThrowsException() {
        String key = "test-key";
        when(configStore.getConfigWithIP(key)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> algorithm.acceptRequest(key));
    }

    @Test
    void acceptRequest_MissingMaxRequestsParameter_ThrowsException() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        assertThrows(NullPointerException.class, () -> algorithm.acceptRequest(key));
    }

    @Test
    void acceptRequest_MissingWindowTimeParameter_ThrowsException() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        assertThrows(NullPointerException.class, () -> algorithm.acceptRequest(key));
    }

    @Test
    void acceptRequest_DifferentKeys_IndependentWindows() {
        String key1 = "key-1";
        String key2 = "key-2";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 2);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key1)).thenReturn(requestConfigDTO);
        when(configStore.getConfigWithIP(key2)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key1)), eq("60"), eq("2"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L));
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key2)), eq("60"), eq("2"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L));

        List<Long> result1 = algorithm.acceptRequest(key1);
        List<Long> result2 = algorithm.acceptRequest(key2);

        assertEquals(1L, result1.get(0));
        assertEquals(1L, result2.get(0));
    }

    @Test
    void acceptRequest_TimeWindowExpiry_AllowsNewRequests() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 3);
        params.put("windowTime", 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("1"), eq("3"), anyString(), eq("2")))
                .thenReturn(Arrays.asList(1L, 0L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));
    }

    @Test
    void acceptRequest_LargeWindowTime_HighTimeWindow() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 100);
        params.put("windowTime", 3600);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("3600"), eq("100"), anyString(), eq("7200")))
                .thenReturn(Arrays.asList(1L, 50L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));
        assertEquals(50L, result.get(1));
    }

    @Test
    void acceptRequest_MinimalMaxRequests_OneRequestPerWindow() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 1);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("1"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("1"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(0L, 1L));

        List<Long> result2 = algorithm.acceptRequest(key);

        assertEquals(0L, result2.get(0));
    }

    @Test
    void acceptRequest_SmallWindow_QuickReset() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowTime", 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("5"), eq("5"), anyString(), eq("10")))
                .thenReturn(Arrays.asList(1L, 2L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));
    }

    @Test
    void getScript_ReturnsCorrectScriptType() {
        RedisScript<List> script = algorithm.getScript();
        assertNotNull(script);
        assertTrue(script instanceof org.springframework.data.redis.core.script.DefaultRedisScript);
    }

    @Test
    void acceptRequest_CurrentTimeParameter_IsPassed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        long currentTime = System.currentTimeMillis();
        
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("10"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("10"), anyString(), eq("120"));
    }

    @Test
    void acceptRequest_ConcurrentCalls_ThreadSafe() throws InterruptedException {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Arrays.asList(1L, 0L));

        Thread[] threads = new Thread[5];
        List<Long>[] results = new List[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = algorithm.acceptRequest(key));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        for (List<Long> result : results) {
            assertNotNull(result);
        }
    }

    @Test
    void acceptRequest_RapidRequests_TracksAllInWindow() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowTime", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"), eq("10"), anyString(), eq("120")))
                .thenReturn(Arrays.asList(1L, 0L))
                .thenReturn(Arrays.asList(1L, 1L))
                .thenReturn(Arrays.asList(1L, 2L))
                .thenReturn(Arrays.asList(1L, 3L))
                .thenReturn(Arrays.asList(1L, 4L))
                .thenReturn(Arrays.asList(1L, 5L))
                .thenReturn(Arrays.asList(1L, 6L))
                .thenReturn(Arrays.asList(1L, 7L))
                .thenReturn(Arrays.asList(1L, 8L))
                .thenReturn(Arrays.asList(1L, 9L))
                .thenReturn(Arrays.asList(0L, 10L));

        for (int i = 0; i < 11; i++) {
            List<Long> result = algorithm.acceptRequest(key);
            if (i < 10) {
                assertEquals(1L, result.get(0), "Request " + (i + 1) + " should be allowed");
            } else {
                assertEquals(0L, result.get(0), "Request " + (i + 1) + " should be blocked");
            }
        }
    }
}
