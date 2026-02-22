package com.vikas.rate_limiter.algorithm;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
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
class FixedCounterRateLimitAlgorithmTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ConfigurationStoreService configStore;

    @Mock
    private RequestConfigDTO requestConfigDTO;

    private FixedCounterRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new FixedCounterRateLimitAlgorithm(redisTemplate, configStore);
    }

    @Test
    void acceptRequest_FirstRequestWithinLimit_ReturnsAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("10"), eq("60")))
                .thenReturn(Arrays.asList(1L, 1L, 60L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0));
        assertEquals(1L, result.get(1));
        assertEquals(60L, result.get(2));
    }

    @Test
    void acceptRequest_RequestAtLimit_ReturnsAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("5"), eq("60")))
                .thenReturn(Arrays.asList(1L, 5L, 60L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(1L, result.get(0));
    }

    @Test
    void acceptRequest_RequestExceedsLimit_ReturnsNotAllowed() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("5"), eq("60")))
                .thenReturn(Arrays.asList(0L, 6L, 30L));

        List<Long> result = algorithm.acceptRequest(key);

        assertNotNull(result);
        assertEquals(0L, result.get(0));
        assertEquals(6L, result.get(1));
    }

    @Test
    void acceptRequest_MultipleRequests_IncrementsCounter() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 3);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("3"), eq("60")))
                .thenReturn(Arrays.asList(1L, 1L, 60L))
                .thenReturn(Arrays.asList(1L, 2L, 60L))
                .thenReturn(Arrays.asList(1L, 3L, 60L))
                .thenReturn(Arrays.asList(0L, 4L, 45L));

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
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        assertThrows(NullPointerException.class, () -> algorithm.acceptRequest(key));
    }

    @Test
    void acceptRequest_MissingWindowSizeParameter_ThrowsException() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        assertThrows(NullPointerException.class, () -> algorithm.acceptRequest(key));
    }

    @Test
    void acceptRequest_DifferentKeys_IndependentCounters() {
        String key1 = "key-1";
        String key2 = "key-2";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 2);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key1)).thenReturn(requestConfigDTO);
        when(configStore.getConfigWithIP(key2)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key1)), eq("2"), eq("60")))
                .thenReturn(Arrays.asList(1L, 1L, 60L));
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key2)), eq("2"), eq("60")))
                .thenReturn(Arrays.asList(1L, 1L, 60L));

        List<Long> result1 = algorithm.acceptRequest(key1);
        List<Long> result2 = algorithm.acceptRequest(key2);

        assertEquals(1L, result1.get(0));
        assertEquals(1L, result2.get(0));
    }

    @Test
    void acceptRequest_WindowExpiry_ResetsCounter() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 5);
        params.put("windowSize", 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("5"), eq("1")))
                .thenReturn(Arrays.asList(1L, 1L, 0L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));
        assertEquals(0L, result.get(2));
    }

    @Test
    void acceptRequest_LargeMaxRequests_HighLimit() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 1000);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("1000"), eq("60")))
                .thenReturn(Arrays.asList(1L, 500L, 60L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));
        assertEquals(500L, result.get(1));
    }

    @Test
    void acceptRequest_MinimalMaxRequests_OneRequest() {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 1);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("1"), eq("60")))
                .thenReturn(Arrays.asList(1L, 1L, 60L));

        List<Long> result = algorithm.acceptRequest(key);

        assertEquals(1L, result.get(0));

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("1"), eq("60")))
                .thenReturn(Arrays.asList(0L, 2L, 45L));

        List<Long> result2 = algorithm.acceptRequest(key);

        assertEquals(0L, result2.get(0));
    }

    @Test
    void getScript_ReturnsCorrectScriptType() {
        RedisScript<List> script = algorithm.getScript();
        assertNotNull(script);
        assertTrue(script instanceof org.springframework.data.redis.core.script.DefaultRedisScript);
    }

    @Test
    void acceptRequest_ConcurrentCalls_ThreadSafe() throws InterruptedException {
        String key = "test-key";
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 10);
        params.put("windowSize", 60);
        
        when(configStore.getConfigWithIP(key)).thenReturn(requestConfigDTO);
        when(requestConfigDTO.getParameters()).thenReturn(params);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(Arrays.asList(1L, 1L, 60L));

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
            assertEquals(1L, result.get(0));
        }
    }
}
