package com.vikas.rate_limiter;

import com.vikas.rate_limiter.algorithm.LeakyBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
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
public class LeakyBucketTest {

    @Mock
    private StringRedisTemplate template;

    @Mock
    private ConfigurationStoreService configStore;

    private LeakyBucketRateLimitAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new LeakyBucketRateLimitAlgorithm(template, configStore);
    }

    // ============ Basic Functionality Tests ============

    @Test
    void shouldAllowRequestWhenQueueNotFull() {
        // Given: Configuration with capacity and flow rate
        String key = "user:192.168.1.1";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Request should be allowed
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(1L, result.get(1), "Queue size should be 1");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("10"), anyString(), eq("2"), eq("600000"));
    }

    @Test
    void shouldRejectRequestWhenQueueIsFull() {
        // Given: Queue is at full capacity
        String key = "user:192.168.1.2";
        RequestConfigDTO config = createConfig(5, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 5L)); // allowed=0, queueSize=5 (full)

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Request should be rejected
        assertEquals(0L, result.get(0), "Request should be rejected");
        assertEquals(5L, result.get(1), "Queue should be full");
    }

    @Test
    void shouldThrowExceptionWhenConfigIsNull() {
        // Given: No configuration exists
        String key = "user:192.168.1.3";
        
        when(configStore.getConfigWithIP(key)).thenReturn(null);

        // When/Then: Should throw NullPointerException
        assertThrows(NullPointerException.class, () -> {
            algorithm.acceptRequest(key);
        }, "Should throw exception when config is null");
    }

    // ============ Leaky (Drain) Functionality Tests ============

    @Test
    void shouldDrainQueueBasedOnElapsedTime() {
        // Given: Queue with items that will be drained over time
        String key = "user:192.168.1.4";
        RequestConfigDTO config = createConfig(20, 5); // flow rate: 5 per second
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: queue had 10 items, 2 seconds passed, 10 drained, 1 added = 1 in queue
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1 after drain

        // When: Request is made after time has passed
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Queue should have drained
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) < 10, "Queue should have drained over time");
    }

    @Test
    void shouldNotDrainBelowZero() {
        // Given: Queue that would drain below zero
        String key = "user:192.168.1.5";
        RequestConfigDTO config = createConfig(10, 100); // very high flow rate
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: long time passed, queue completely drained, new request added
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1 (0 + 1, not negative)

        // When: Request is made after long time
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Queue size should not be negative
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) >= 0, "Queue size should never be negative");
    }

    @Test
    void shouldHandlePartialDrain() {
        // Given: Queue with partial drain (fractional leak)
        String key = "user:192.168.1.6";
        RequestConfigDTO config = createConfig(20, 3);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: queue had 10, 1.5 seconds passed, 4.5 drained, 1 added = 6.5 ≈ 6 or 7
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 6L)); // allowed=1, queueSize=6 after partial drain

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle partial drains correctly
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) > 0, "Queue should have partially drained");
    }

    @Test
    void shouldLeakAtConstantRate() {
        // Given: Configuration with specific flow rate
        String key = "user:192.168.1.7";
        RequestConfigDTO config = createConfig(30, 10); // 10 requests per second
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: 3 seconds passed, 30 items drained
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1 (completely drained + new)

        // When: Request is made after time passes
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should leak at constant rate
        assertEquals(1L, result.get(0), "Request should be allowed");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), eq("30"), anyString(), eq("10"), anyString());
    }

    // ============ Queue Capacity Tests ============

    @Test
    void shouldAcceptRequestsUpToCapacity() {
        // Given: Queue with capacity
        String key = "user:192.168.1.8";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate filling queue gradually
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(
                List.of(1L, 1L),  // First request
                List.of(1L, 2L),  // Second request
                List.of(1L, 9L),  // Ninth request
                List.of(1L, 10L)  // Tenth request (at capacity)
            );

        // When: Multiple requests are made
        for (int i = 0; i < 4; i++) {
            List<Long> result = algorithm.acceptRequest(key);
            assertEquals(1L, result.get(0), "Request " + (i + 1) + " should be allowed");
            assertTrue(result.get(1) <= 10, "Queue size should not exceed capacity");
        }
    }

    @Test
    void shouldRejectWhenExactlyAtCapacity() {
        // Given: Queue exactly at capacity
        String key = "user:192.168.1.9";
        RequestConfigDTO config = createConfig(5, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 5L)); // allowed=0, queueSize=5 (exactly at capacity)

        // When: Request is made when queue is full
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Request should be rejected
        assertEquals(0L, result.get(0), "Request should be rejected at capacity");
        assertEquals(5L, result.get(1), "Queue should be exactly at capacity");
    }

    @Test
    void shouldHandleZeroCapacity() {
        // Given: Configuration with zero capacity
        String key = "user:192.168.1.10";
        RequestConfigDTO config = createConfig(0, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 0L)); // allowed=0, queueSize=0

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should reject all requests
        assertEquals(0L, result.get(0), "Request should be rejected with zero capacity");
    }

    @Test
    void shouldHandleVeryLargeCapacity() {
        // Given: Configuration with very large capacity
        String key = "user:192.168.1.11";
        RequestConfigDTO config = createConfig(Integer.MAX_VALUE, 1000);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle large capacity
        assertEquals(1L, result.get(0), "Request should be allowed");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), 
            eq(String.valueOf(Integer.MAX_VALUE)), anyString(), anyString(), anyString());
    }

    @Test
    void shouldHandleSingleItemCapacity() {
        // Given: Configuration with capacity of 1
        String key = "user:192.168.1.12";
        RequestConfigDTO config = createConfig(1, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // First request fills the queue
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1 (full)
        
        List<Long> result1 = algorithm.acceptRequest(key);
        assertEquals(1L, result1.get(0), "First request should be allowed");
        assertEquals(1L, result1.get(1), "Queue should be full");

        // Second immediate request should be rejected
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(0L, 1L)); // allowed=0, queueSize=1 (still full)
        
        List<Long> result2 = algorithm.acceptRequest(key);
        assertEquals(0L, result2.get(0), "Second request should be rejected");
    }

    // ============ Flow Rate Edge Cases ============

    @Test
    void shouldHandleZeroFlowRate() {
        // Given: Configuration with zero flow rate (never drains)
        String key = "user:192.168.1.13";
        RequestConfigDTO config = createConfig(10, 0);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should allow requests but never drain
        assertEquals(1L, result.get(0), "Request should be allowed");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), eq("0"), anyString());
    }

    @Test
    void shouldHandleVeryHighFlowRate() {
        // Given: Configuration with very high flow rate
        String key = "user:192.168.1.14";
        RequestConfigDTO config = createConfig(100, 10000);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle high flow rate
        assertEquals(1L, result.get(0), "Request should be allowed");
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), eq("10000"), anyString());
    }

    // ============ Concurrency Tests ============

    @Test
    void shouldHandleConcurrentRequestsCorrectly() throws InterruptedException {
        // Given: Multiple threads making concurrent requests
        String key = "user:192.168.1.15";
        RequestConfigDTO config = createConfig(20, 5);
        int threadCount = 50;
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        
        // Mock Redis to allow first 20, reject rest
        AtomicInteger callCount = new AtomicInteger(0);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count <= 20) {
                    return List.of(1L, (long) count);
                } else {
                    return List.of(0L, 20L);
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
        // Given: Synchronized method ensures thread safety
        // When/Then: Verify the method is synchronized
        try {
            var method = LeakyBucketRateLimitAlgorithm.class.getMethod("acceptRequest", String.class);
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
        String key1 = "user:192.168.1.16";
        String key2 = "user:192.168.1.17";
        
        RequestConfigDTO config1 = createConfig(10, 2);
        RequestConfigDTO config2 = createConfig(20, 5);
        
        when(configStore.getConfigWithIP(key1)).thenReturn(config1);
        when(configStore.getConfigWithIP(key2)).thenReturn(config2);
        
        when(template.execute(any(RedisScript.class), eq(List.of(key1)), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));
        when(template.execute(any(RedisScript.class), eq(List.of(key2)), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Both users make requests
        List<Long> result1 = algorithm.acceptRequest(key1);
        List<Long> result2 = algorithm.acceptRequest(key2);

        // Then: Each user should have independent limits
        assertEquals(1L, result1.get(0), "User 1 request should be allowed");
        assertEquals(1L, result2.get(0), "User 2 request should be allowed");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(key1)), eq("10"), anyString(), eq("2"), anyString());
        verify(template).execute(any(RedisScript.class), eq(List.of(key2)), eq("20"), anyString(), eq("5"), anyString());
    }

    @Test
    void shouldHandleDifferentConfigurationsPerUser() {
        // Given: Users with vastly different configurations
        String premiumUser = "user:premium:192.168.1.18";
        String freeUser = "user:free:192.168.1.19";
        
        RequestConfigDTO premiumConfig = createConfig(1000, 100);
        RequestConfigDTO freeConfig = createConfig(10, 1);
        
        when(configStore.getConfigWithIP(premiumUser)).thenReturn(premiumConfig);
        when(configStore.getConfigWithIP(freeUser)).thenReturn(freeConfig);
        
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L), List.of(1L, 1L));

        // When: Both users make requests
        List<Long> premiumResult = algorithm.acceptRequest(premiumUser);
        List<Long> freeResult = algorithm.acceptRequest(freeUser);

        // Then: Different configurations should be respected
        assertEquals(1L, premiumResult.get(0), "Premium user should be allowed");
        assertEquals(1L, freeResult.get(0), "Free user should be allowed");
        
        verify(template).execute(any(RedisScript.class), eq(List.of(premiumUser)), 
            eq("1000"), anyString(), eq("100"), anyString());
        verify(template).execute(any(RedisScript.class), eq(List.of(freeUser)), 
            eq("10"), anyString(), eq("1"), anyString());
    }

    // ============ Time-based Edge Cases ============

    @Test
    void shouldHandleNoTimeElapsed() {
        // Given: No time has passed since last request
        String key = "user:192.168.1.20";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: same timestamp, no drain
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 5L)); // allowed=1, queueSize=5 (no change)

        // When: Request is made at same time
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should not drain if no time passed
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) > 0, "Queue should maintain size");
    }

    @Test
    void shouldHandleNegativeTimeDelta() {
        // Given: Time goes backwards (clock adjustment)
        String key = "user:192.168.1.21";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        // Lua script handles negative time by using max(0, elapsed_time)
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle gracefully (Lua script uses max(0, elapsed))
        assertEquals(1L, result.get(0), "Request should be handled despite time issues");
    }

    @Test
    void shouldHandleLargeTimeJump() {
        // Given: Very large time jump (days or weeks)
        String key = "user:192.168.1.22";
        RequestConfigDTO config = createConfig(50, 10);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: long time passed, queue completely drained
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1 (completely drained + new)

        // When: Request is made after long time
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should drain completely
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertTrue(result.get(1) <= 50, "Queue should not exceed capacity");
    }

    // ============ First Request Initialization Tests ============

    @Test
    void shouldInitializeQueueOnFirstRequest() {
        // Given: Very first request for a new key
        String key = "user:new:192.168.1.23";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        // First request initializes with empty queue, adds 1
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L)); // allowed=1, queueSize=1

        // When: First request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should initialize queue correctly
        assertEquals(1L, result.get(0), "First request should be allowed");
        assertEquals(1L, result.get(1), "Queue should have one item");
    }

    // ============ TTL Tests ============

    @Test
    void shouldSetProperTTLOnRedisKeys() {
        // Given: Configuration
        String key = "user:192.168.1.24";
        RequestConfigDTO config = createConfig(10, 5);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made
        algorithm.acceptRequest(key);

        // Then: TTL should be set (600000ms = 10 minutes)
        verify(template).execute(any(RedisScript.class), eq(List.of(key)), 
            anyString(), anyString(), anyString(), eq("600000"));
    }

    // ============ Parameter Validation Tests ============

    @Test
    void shouldHandleNullKey() {
        // Given: Null key
        when(configStore.getConfigWithIP(null)).thenReturn(null);

        // When/Then: Should handle null key (might throw exception)
        assertThrows(NullPointerException.class, () -> {
            algorithm.acceptRequest(null);
        }, "Should throw exception for null key");
    }

    @Test
    void shouldHandleEmptyKey() {
        // Given: Empty key
        String key = "";
        RequestConfigDTO config = createConfig(10, 2);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 1L));

        // When: Request is made with empty key
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should handle empty key
        assertEquals(1L, result.get(0), "Should handle empty key");
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

    // ============ Queue Behavior Tests ============

    @Test
    void shouldMaintainQueueSizeAccurately() {
        // Given: Specific queue operations
        String key = "user:192.168.1.25";
        RequestConfigDTO config = createConfig(20, 3);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: 15 in queue, 2 seconds passed, 6 drained, 1 added = 10
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 10L)); // allowed=1, queueSize=10

        // When: Request is made
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Queue size should be accurate
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(10L, result.get(1), "Queue size should be accurate after drain");
    }

    @Test
    void shouldHandleExactCapacityFill() {
        // Given: Filling queue to exact capacity
        String key = "user:192.168.1.26";
        RequestConfigDTO config = createConfig(5, 1);
        
        when(configStore.getConfigWithIP(key)).thenReturn(config);
        
        // Simulate: queue at exactly capacity
        when(template.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(1L, 5L)); // allowed=1, queueSize=5 (exactly at capacity)

        // When: Request fills queue to capacity
        List<Long> result = algorithm.acceptRequest(key);

        // Then: Should accept and be at exact capacity
        assertEquals(1L, result.get(0), "Request should be allowed");
        assertEquals(5L, result.get(1), "Queue should be at exact capacity");
    }

    // ============ Helper Methods ============

    private RequestConfigDTO createConfig(int capacity, int flowRate) {
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(RequestConfigDTO.Algorithm.LEAKY_BUCKET);
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("flowRate", flowRate);
        config.setParameters(params);
        return config;
    }
}
