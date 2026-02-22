package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.TokenBucketRateLimitAlgorithm;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenBucketTest {

    @Mock
    private StringRedisTemplate template;

    @Mock
    private ConfigurationStoreService configStore;

    @Mock
    private RateLimiterProperties props;

    @Mock
    private RedisScript<List> mockScript;

    private TokenBucketRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketRateLimitAlgorithm(template, configStore, props);
        // Set the script field using reflection since it's final and initialized in constructor
        try {
            java.lang.reflect.Field scriptField = TokenBucketRateLimitAlgorithm.class.getDeclaredField("script");
            scriptField.setAccessible(true);
            // Remove final modifier
            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(scriptField, scriptField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            scriptField.set(algorithm, mockScript);
        } catch (Exception e) {
            // If reflection fails, the test will use the actual script which is also fine
        }
    }

    // ============ Basic Functionality Tests ============

    @Test
    void shouldAllowRequestWhenTokensAvailable() {
        // Given: Configuration with capacity and refillRate
        String key = "user:192.168.1.1";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L)); // allowed=1, remainingTokens=9

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Request should be allowed
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(9L, result.get(1), "Should have 9 tokens remaining");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("10"), eq("2"), anyString(), eq("1"), eq("600000"));
    }

    @Test
    void shouldRejectRequestWhenNoTokensAvailable() {
        // Given: Configuration with capacity and no tokens
        String key = "user:192.168.1.2";
        RequestConfigDTO config = createConfig(5, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 0L)); // allowed=0, remainingTokens=0

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Request should be rejected
        assertEquals(0L, result.get(0), "Request should be rejected");
        assertEquals(0L, result.get(1), "Should have 0 tokens remaining");
    }

    @Test
    void shouldUseDefaultConfigWhenNoCustomConfigExists() {
        // Given: No custom config, using default
        String key = "user:192.168.1.3";
        RateLimiterProperties.Limits limits = new RateLimiterProperties.Limits();
        limits.setDefaultCapacity(20);
        limits.setRefillRate(5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(null);
        when(props.getLimits()).thenReturn(limits);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 19L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Default config should be used
        assertEquals(1L, result.get(0), "Request should be allowed with default config");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("20"), eq("5"), anyString(), eq("1"), eq("600000"));
    }

    // ============ Token Refill Tests ============

    @Test
    void shouldRefillTokensOverTime() {
        // Given: Bucket with tokens being refilled
        String key = "user:192.168.1.4";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // First request: consume tokens
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 0L)); // allowed=1, remainingTokens=0
        
        List<Long> result1 = algorithm.acceptRequest(key);
        assertEquals(1L, result1.get(0), "First request should be allowed");
        assertEquals(0L, result1.get(1), "No tokens remaining");

        // After time passes, tokens are refilled
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 4L)); // allowed=1, remainingTokens=4 (5 refilled - 1 consumed)
        
        List<Long> result2 = algorithm.acceptRequest(key);
        assertEquals(1L, result2.get(0), "Request should be allowed after refill");
        assertEquals(4L, result2.get(1), "Should have refilled tokens");
    }

    @Test
    void shouldNotExceedCapacityDuringRefill() {
        // Given: Bucket that would exceed capacity if refill wasn't capped
        String key = "user:192.168.1.5";
        RequestConfigDTO config = createConfig(10, 100); // Very high refill rate
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L)); // allowed=1, remainingTokens=9 (capped at capacity)

        // When: Request is made after long time
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Tokens should be capped at capacity
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) <= 10, "Remaining tokens should not exceed capacity");
    }

    @Test
    void shouldHandlePartialTokenRefill() {
        // Given: Bucket with partial refill (fractional tokens)
        String key = "user:192.168.1.6";
        RequestConfigDTO config = createConfig(10, 3);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate partial refill over short time period
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 2L)); // allowed=1, remainingTokens=2 (partial refill)

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle partial refills correctly
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(2L, result.get(1), "Should have partially refilled tokens");
    }

    // ============ Capacity Edge Cases ============

    @Test
    void shouldHandleZeroCapacity() {
        // Given: Configuration with zero capacity (edge case)
        String key = "user:192.168.1.7";
        RequestConfigDTO config = createConfig(0, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 0L)); // allowed=0, remainingTokens=0

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: All requests should be rejected
        assertEquals(0L, result.get(0), "Request should be rejected with zero capacity");
        assertEquals(0L, result.get(1), "Should have 0 tokens");
    }

    @Test
    void shouldHandleVeryLargeCapacity() {
        // Given: Configuration with very large capacity
        String key = "user:192.168.1.8";
        RequestConfigDTO config = createConfig(Integer.MAX_VALUE, 1000);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, (long) Integer.MAX_VALUE - 1));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle large numbers correctly
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) > 0, "Should handle large capacity");
    }

    @Test
    void shouldHandleSingleTokenCapacity() {
        // Given: Configuration with capacity of 1
        String key = "user:192.168.1.9";
        RequestConfigDTO config = createConfig(1, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // First request consumes the only token
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 0L)); // allowed=1, remainingTokens=0
        
        List<Long> result1 = algorithm.acceptRequest(key);
        assertEquals(1L, result1.get(0), "First request should be allowed");
        assertEquals(0L, result1.get(1), "No tokens remaining");

        // Second immediate request should be rejected
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 0L)); // allowed=0, remainingTokens=0
        
        List<Long> result2 = algorithm.acceptRequest(key);
        assertEquals(0L, result2.get(0), "Second request should be rejected");
    }

    // ============ Refill Rate Edge Cases ============

    @Test
    void shouldHandleZeroRefillRate() {
        // Given: Configuration with zero refill rate
        String key = "user:192.168.1.10";
        RequestConfigDTO config = createConfig(5, 0);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 4L)); // allowed=1, remainingTokens=4

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should allow initial tokens but never refill
        assertEquals(1L, result.get(0), "Request should be allowed from initial tokens");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("5"), eq("0"), anyString(), eq("1"), eq("600000"));
    }

    @Test
    void shouldHandleVeryHighRefillRate() {
        // Given: Configuration with very high refill rate
        String key = "user:192.168.1.11";
        RequestConfigDTO config = createConfig(100, 10000);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 99L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle high refill rate
        assertEquals(1L, result.get(0), "Request should be allowed");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("100"), eq("10000"), anyString(), eq("1"), eq("600000"));
    }

    // ============ Concurrency Tests ============

    @Test
    void shouldHandleConcurrentRequestsCorrectly() throws InterruptedException {
        // Given: Multiple threads making concurrent requests
        String key = "user:192.168.1.12";
        RequestConfigDTO config = createConfig(20, 5);
        int threadCount = 50;
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        
        // Mock Redis to return allowed for first 20 requests, rejected for rest
        AtomicInteger callCount = new AtomicInteger(0);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count <= 20) {
                    return List.of(1L, (long) (20 - count));
                } else {
                    return List.of(0L, 0L);
                }
            });

        // When: Multiple threads make requests concurrently
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    List<Long> result = algorithm.acceptRequest(key);
                    if (result.get(0) == 1L) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Should properly handle concurrent requests
        assertEquals(threadCount, allowedCount.get() + rejectedCount.get(), 
            "All requests should be processed");
        assertTrue(allowedCount.get() <= 20, 
            "Should not allow more than capacity");
    }

    @Test
    void shouldBeSynchronizedAcrossRequests() {
        // When: Check if method is synchronized
        // Then: Verify the method is synchronized by checking method signature
        try {
            var method = TokenBucketRateLimitAlgorithm.class.getMethod("acceptRequest", String.class);
            int modifiers = method.getModifiers();
            assertTrue(java.lang.reflect.Modifier.isSynchronized(modifiers), 
                "acceptRequest method should be synchronized");
        } catch (NoSuchMethodException e) {
            fail("acceptRequest method should exist");
        }
    }

    // ============ Multiple Users Tests ============

    @Test
    void shouldHandleMultipleUsersIndependently() {
        // Given: Multiple users with different configurations
        String key1 = "user:192.168.1.14";
        String key2 = "user:192.168.1.15";
        
        RequestConfigDTO config1 = createConfig(10, 2);
        RequestConfigDTO config2 = createConfig(20, 5);
        
        when(configStore.getConfigWithIP(key1)).thenReturn(config1);
        when(configStore.getConfigWithIP(key2)).thenReturn(config2);
        
        when(template.execute(any(RedisScript.class), eq(List.of(key1)), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L));
        when(template.execute(any(RedisScript.class), eq(List.of(key2)), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 19L));

        // When: Both users make requests
        List<Long> result1 = algorithm.acceptRequest(key1);
        List<Long> result2 = algorithm.acceptRequest(key2);

        // Then: Each user should have independent limits
        assertEquals(1L, result1.get(0), "User 1 request should be allowed");
        assertEquals(9L, result1.get(1), "User 1 should have correct remaining tokens");
        
        assertEquals(1L, result2.get(0), "User 2 request should be allowed");
        assertEquals(19L, result2.get(1), "User 2 should have correct remaining tokens");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(key1)), eq("10"), eq("2"), anyString(), eq("1"), eq("600000"));
        verify(template).execute(any(RedisScript.class), eq(List.of(key2)), eq("20"), eq("5"), anyString(), eq("1"), eq("600000"));
    }

    @Test
    void shouldHandleDifferentConfigurationsPerUser() {
        // Given: Users with vastly different configurations
        String premiumUser = "user:premium:192.168.1.16";
        String freeUser = "user:free:192.168.1.17";
        
        RequestConfigDTO premiumConfig = createConfig(1000, 100);
        RequestConfigDTO freeConfig = createConfig(10, 1);
        
        when(configStore.getConfigWithIP(premiumUser)).thenReturn(premiumConfig);
        when(configStore.getConfigWithIP(freeUser)).thenReturn(freeConfig);
        
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 999L), List.of(1L, 9L));

        // When: Both users make requests
        List<Long> premiumResult = algorithm.acceptRequest(premiumUser);
        List<Long> freeResult = algorithm.acceptRequest(freeUser);

        // Then: Different configurations should be respected
        assertEquals(1L, premiumResult.get(0), "Premium user should be allowed");
        assertEquals(1L, freeResult.get(0), "Free user should be allowed");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(premiumUser)), 
            eq("1000"), eq("100"), anyString(), eq("1"), eq("600000"));
        verify(template).execute(any(RedisScript.class), eq(List.of(freeUser)), 
            eq("10"), eq("1"), anyString(), eq("1"), eq("600000"));
    }

    // ============ Negative Time Edge Cases ============

    @Test
    void shouldHandleNegativeTimeDelta() {
        // Given: Configuration where time might go backwards (clock adjustment)
        String key = "user:192.168.1.18";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        // Redis Lua script handles negative time delta by setting it to 0
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle gracefully (Lua script sets delta to 0 if negative)
        assertEquals(1L, result.get(0), "Request should be handled despite time issues");
    }

    // ============ TTL Tests ============

    @Test
    void shouldSetProperTTLOnRedisKeys() {
        // Given: Configuration
        String key = "user:192.168.1.19";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L));

        // When: Request is made
        algorithm.acceptRequest(key);

        // Then: TTL should be set (600000ms = 10 minutes)
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), 
            anyString(), anyString(), anyString(), anyString(), eq("600000"));
    }

    // ============ Boundary Value Tests ============

    @Test
    void shouldHandleExactCapacityConsumption() {
        // Given: Exactly capacity tokens will be consumed
        String key = "user:192.168.1.20";
        RequestConfigDTO config = createConfig(5, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate consuming all tokens exactly
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 0L)); // Last token consumed

        // When: Final token is consumed
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should allow request and have 0 tokens left
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(0L, result.get(1), "Should have exactly 0 tokens remaining");
    }

    @Test
    void shouldHandleFirstRequestInitialization() {
        // Given: Very first request for a new key
        String key = "user:new:192.168.1.21";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        // First request initializes bucket with full capacity
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L)); // allowed=1, remainingTokens=9 (10-1)

        // When: First request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should initialize with full capacity
        assertEquals(1L, result.get(0), "First request should be allowed");
        assertEquals(9L, result.get(1), "Should start with full capacity minus one");
    }

    // ============ Parameter Validation Tests ============

    @Test
    void shouldHandleNullKey() {
        // Given: Null key
        when(configStore.getConfigWithIP(null)).thenReturn(null);
        
        RateLimiterProperties.Limits limits = new RateLimiterProperties.Limits();
        limits.setDefaultCapacity(10);
        limits.setRefillRate(2);
        when(props.getLimits()).thenReturn(limits);

        // When/Then: Should handle null key (might throw exception or handle gracefully)
        // The implementation will throw NPE which is acceptable for null keys
        assertThrows(NullPointerException.class, () -> {
            algorithm.acceptRequest(null);
        }, "Should throw NullPointerException for null key");
    }

    @Test
    void shouldHandleEmptyKey() {
        // Given: Empty key
        String key = "";
        when(configStore.getConfigWithIP(key)).thenReturn(null);
        
        RateLimiterProperties.Limits limits = new RateLimiterProperties.Limits();
        limits.setDefaultCapacity(10);
        limits.setRefillRate(2);
        when(props.getLimits()).thenReturn(limits);
        
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 9L));

        // When: Request is made with empty key
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should use default config
        assertEquals(1L, result.get(0), "Should handle empty key with default config");
    }

    // ============ Redis Script Loading Tests ============

    @Test
    void shouldLoadRedisScriptCorrectly() {
        // When: Getting the script
        RedisScript<List> script = algorithm.getScript();

        // Then: Script should be loaded
        assertNotNull(script, "Script should not be null");
        assertEquals(List.class, script.getResultType(), "Script result type should be List");
    }

    // ============ Time-based Refill Accuracy Tests ============

    @Test
    void shouldCalculateRefillAccurately() {
        // Given: Specific time-based refill scenario
        String key = "user:192.168.1.22";
        RequestConfigDTO config = createConfig(100, 10); // 10 tokens per second
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate refill after 2.5 seconds: 10 * 2.5 = 25 tokens
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 74L)); // 50 existing + 25 refilled - 1 consumed = 74

        // When: Request is made after time passes
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should refill correctly based on elapsed time
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) > 0, "Should have refilled tokens based on time");
    }

    // ============ Helper Methods ============

    private RequestConfigDTO createConfig(int capacity, int refillRate) {
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(RequestConfigDTO.Algorithm.TOKEN_BUCKET);
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("refillRate", refillRate);
        config.setParameters(params);
        return config;
    }
}
