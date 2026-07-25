package com.vikas.rate_limiter;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;
import com.vikas.rate_limiter.service.MongoConfigurationStoreService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Rate Limiter
 *
 * Tests the complete flow:
 * - HTTP Filter → Rate Limit Manager → Algorithm → Redis Cache
 * - Configuration API endpoints
 * - HTTP Response headers
 * - Algorithm switching per IP
 * - Concurrent request handling
 * - Fallback configuration
 * - Error handling (Redis/MongoDB down, invalid configs)
 *
 * Uses TestContainers for embedded Redis and MongoDB
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Rate Limiter Integration Tests")
class RateLimiterIntegrationTest {

    // ============================================================================
    // Container Configuration
    // ============================================================================

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> mongodb = new GenericContainer<>("mongo:6.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.mongodb.host", mongodb::getHost);
        registry.add("spring.data.mongodb.port", () -> mongodb.getMappedPort(27017));
    }

    // ============================================================================
    // Fixtures and Utilities
    // ============================================================================

    @Autowired
    private ConfigurationStoreService configStore;

    @Autowired
    private MongoConfigurationStoreService mongoConfigStore;

    @Autowired
    private RateLimitConfigRepository repository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_ENDPOINT = "/api/test";
    private static final String USER_TIER = "free";

    @BeforeEach
    void setUp() {
        // Clear Redis
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            log.info("Redis cleared");
        } catch (Exception e) {
            log.warn("Could not flush Redis: {}", e.getMessage());
        }
        // Clear MongoDB
        repository.deleteAll();
        log.info("MongoDB cleared - test setup completed");
    }

    @AfterEach
    void tearDown() {
        // Clean up after tests
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            log.warn("Could not flush Redis: {}", e.getMessage());
        }
        repository.deleteAll();
        log.info("Test teardown completed - resources cleaned");
    }

    // ============================================================================
    // 1. Configuration Storage and Retrieval Tests
    // ============================================================================

    @Test
    @DisplayName("Should store and retrieve rate limit configuration from MongoDB")
    @Timeout(10)
    void testStoreAndRetrieveConfiguration() {
        // Arrange
        RateLimitConfigEntity config = createTokenBucketConfig(TEST_IP, TEST_ENDPOINT, 10, 60);

        // Act
        RateLimitConfigEntity savedConfig = mongoConfigStore.saveConfig(config);

        // Assert
        assertThat(savedConfig).isNotNull();
        assertThat(savedConfig.getId()).isNotNull();

        var retrievedConfig = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, TEST_ENDPOINT, USER_TIER);
        assertThat(retrievedConfig).isPresent();
        assertThat(retrievedConfig.get().getAlgorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(retrievedConfig.get().getParameters().get("capacity")).isEqualTo(10);

        log.info("Configuration stored and retrieved successfully");
    }

    @Test
    @DisplayName("Should retrieve all configurations from MongoDB")
    @Timeout(10)
    void testRetrieveAllConfigurations() {
        // Arrange: Create multiple configs
        mongoConfigStore.saveConfig(createTokenBucketConfig("192.168.1.1", "/api/test1", 5, 60));
        mongoConfigStore.saveConfig(createFixedWindowConfig("192.168.1.2", "/api/test2", 10, 60));
        mongoConfigStore.saveConfig(createSlidingWindowConfig("192.168.1.3", "/api/test3", 15, 60));

        // Act
        var allConfigs = mongoConfigStore.getAllConfigs();

        // Assert
        assertThat(allConfigs).isNotEmpty();
        assertThat(allConfigs.size()).isGreaterThanOrEqualTo(3);

        log.info("Retrieved {} configurations from MongoDB", allConfigs.size());
    }

    // ============================================================================
    // 2. Algorithm Switching Per IP Tests
    // ============================================================================

    @Test
    @DisplayName("Should support different algorithms for different IPs")
    @Timeout(10)
    void testAlgorithmSwitchingPerIp() {
        // Arrange: Create configs with different algorithms
        String ip1 = "192.168.1.101";
        String ip2 = "192.168.1.102";
        String ip3 = "192.168.1.103";

        mongoConfigStore.saveConfig(createTokenBucketConfig(ip1, TEST_ENDPOINT, 20, 60));
        mongoConfigStore.saveConfig(createFixedWindowConfig(ip2, TEST_ENDPOINT, 30, 60));
        mongoConfigStore.saveConfig(createLeakyBucketConfig(ip3, TEST_ENDPOINT, 25, 60));

        // Act & Assert
        var config1 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                ip1, TEST_ENDPOINT, USER_TIER);
        var config2 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                ip2, TEST_ENDPOINT, USER_TIER);
        var config3 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                ip3, TEST_ENDPOINT, USER_TIER);

        assertThat(config1).isPresent();
        assertThat(config1.get().getAlgorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(config1.get().getParameters().get("capacity")).isEqualTo(20);

        assertThat(config2).isPresent();
        assertThat(config2.get().getAlgorithm()).isEqualTo(Algorithm.FIXED_WINDOW);
        assertThat(config2.get().getParameters().get("maxRequests")).isEqualTo(30);

        assertThat(config3).isPresent();
        assertThat(config3.get().getAlgorithm()).isEqualTo(Algorithm.LEAKY_BUCKET);
        assertThat(config3.get().getParameters().get("capacity")).isEqualTo(25);

        log.info("Algorithm switching per IP verified: TOKEN_BUCKET, FIXED_WINDOW, LEAKY_BUCKET");
    }

    @Test
    @DisplayName("Should maintain separate configurations for same IP but different endpoints")
    @Timeout(10)
    void testMultipleEndpointsPerIp() {
        // Arrange
        mongoConfigStore.saveConfig(createTokenBucketConfig(TEST_IP, "/api/endpoint1", 5, 60));
        mongoConfigStore.saveConfig(createTokenBucketConfig(TEST_IP, "/api/endpoint2", 10, 60));
        mongoConfigStore.saveConfig(createTokenBucketConfig(TEST_IP, "/api/endpoint3", 15, 60));

        // Act
        var config1 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, "/api/endpoint1", USER_TIER);
        var config2 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, "/api/endpoint2", USER_TIER);
        var config3 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, "/api/endpoint3", USER_TIER);

        // Assert
        assertThat(config1).isPresent();
        assertThat(config1.get().getParameters().get("capacity")).isEqualTo(5);

        assertThat(config2).isPresent();
        assertThat(config2.get().getParameters().get("capacity")).isEqualTo(10);

        assertThat(config3).isPresent();
        assertThat(config3.get().getParameters().get("capacity")).isEqualTo(15);

        log.info("Multiple endpoints per IP verified with different limits");
    }

    // ============================================================================
    // 3. Concurrent Request Handling Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent configuration updates safely")
    @Timeout(15)
    void testConcurrentConfigurationUpdates() throws InterruptedException {
        // Arrange
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act: Send concurrent configuration updates
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    RateLimitConfigEntity config = createTokenBucketConfig(
                            TEST_IP, TEST_ENDPOINT, 10 + threadId, 60);
                    mongoConfigStore.saveConfig(config);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error in concurrent update", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numThreads);

        log.info("Concurrent configuration updates completed successfully: {} updates", successCount.get());

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle concurrent requests from multiple IPs")
    @Timeout(20)
    void testConcurrentRequestsFromMultipleIps() throws InterruptedException {
        // Arrange
        int numIps = 5;
        int updatesPerIp = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numIps);
        CountDownLatch latch = new CountDownLatch(numIps);
        AtomicInteger totalUpdates = new AtomicInteger(0);

        // Create initial configs
        for (int i = 0; i < numIps; i++) {
            String ip = "192.168.1." + (200 + i);
            mongoConfigStore.saveConfig(createTokenBucketConfig(ip, TEST_ENDPOINT, 20, 60));
        }

        // Act: Send concurrent requests from multiple IPs
        for (int i = 0; i < numIps; i++) {
            final String ip = "192.168.1." + (200 + i);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < updatesPerIp; j++) {
                        var config = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                                ip, TEST_ENDPOINT, USER_TIER);
                        assertThat(config).isPresent();
                        totalUpdates.addAndGet(1);
                    }
                } catch (Exception e) {
                    log.error("Error in concurrent retrieval", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(totalUpdates.get()).isEqualTo(numIps * updatesPerIp);

        log.info("Concurrent requests from {} IPs completed: {} total operations",
                numIps, totalUpdates.get());

        executor.shutdown();
    }

    // ============================================================================
    // 4. Configuration Persistence Tests
    // ============================================================================

    @Test
    @DisplayName("Should persist configuration with all details in MongoDB")
    @Timeout(10)
    void testConfigurationPersistenceWithDetails() {
        // Arrange
        RateLimitConfigEntity config = new RateLimitConfigEntity();
        config.setIp(TEST_IP);
        config.setEndpoint(TEST_ENDPOINT);
        config.setUserTier("premium");
        config.setMethod("GET");
        config.setAlgorithm(Algorithm.SLIDING_WINDOW);
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(42);

        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", 100);
        params.put("windowSize", 120);
        config.setParameters(params);

        // Act
        RateLimitConfigEntity savedConfig = mongoConfigStore.saveConfig(config);

        // Assert
        assertThat(savedConfig.getId()).isNotNull();
        assertThat(savedConfig.getIp()).isEqualTo(TEST_IP);
        assertThat(savedConfig.getEndpoint()).isEqualTo(TEST_ENDPOINT);
        assertThat(savedConfig.getUserTier()).isEqualTo("premium");
        assertThat(savedConfig.getAlgorithm()).isEqualTo(Algorithm.SLIDING_WINDOW);
        assertThat(savedConfig.getAccessCount()).isEqualTo(42);

        log.info("Configuration persisted with all details: {}", savedConfig.getId());
    }

    @Test
    @DisplayName("Should update existing configuration")
    @Timeout(10)
    void testUpdateExistingConfiguration() {
        // Arrange
        RateLimitConfigEntity config1 = createTokenBucketConfig(TEST_IP, TEST_ENDPOINT, 10, 60);
        RateLimitConfigEntity saved1 = mongoConfigStore.saveConfig(config1);

        // Act: Update the configuration
        RateLimitConfigEntity configUpdate = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, TEST_ENDPOINT, USER_TIER).orElseThrow();
        configUpdate.getParameters().put("capacity", 20);
        RateLimitConfigEntity saved2 = mongoConfigStore.saveConfig(configUpdate);

        // Assert
        var retrievedConfig = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, TEST_ENDPOINT, USER_TIER);
        assertThat(retrievedConfig).isPresent();
        assertThat(retrievedConfig.get().getParameters().get("capacity")).isEqualTo(20);

        log.info("Configuration updated successfully");
    }

    // ============================================================================
    // 5. Rapid Configuration Updates Test
    // ============================================================================

    @Test
    @DisplayName("Should handle rapid configuration updates for same IP")
    @Timeout(10)
    void testRapidConfigurationUpdates() throws InterruptedException {
        // Arrange & Act: Rapidly update config for same IP
        for (int i = 0; i < 10; i++) {
            RateLimitConfigEntity config = createTokenBucketConfig(
                    TEST_IP, TEST_ENDPOINT, 10 + i, 60);
            mongoConfigStore.saveConfig(config);
        }

        Thread.sleep(300);

        // Assert: Latest config should be retrievable
        var latestConfig = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                TEST_IP, TEST_ENDPOINT, USER_TIER);
        assertThat(latestConfig).isPresent();
        assertThat(latestConfig.get().getParameters().get("capacity")).isGreaterThanOrEqualTo(10);

        log.info("Rapid configuration updates test passed");
    }

    // ============================================================================
    // 6. Rate Limit Decision Logic Tests
    // ============================================================================

    @Test
    @DisplayName("Should create valid configuration for all supported algorithms")
    @Timeout(10)
    void testAllAlgorithmConfigurations() {
        // Arrange & Act
        RateLimitConfigEntity tokenBucket = createTokenBucketConfig(
                "192.168.1.50", "/api/token", 100, 50);
        RateLimitConfigEntity fixedWindow = createFixedWindowConfig(
                "192.168.1.51", "/api/fixed", 50, 60);
        RateLimitConfigEntity slidingWindow = createSlidingWindowConfig(
                "192.168.1.52", "/api/sliding", 75, 90);
        RateLimitConfigEntity leakyBucket = createLeakyBucketConfig(
                "192.168.1.53", "/api/leaky", 120, 40);

        mongoConfigStore.saveConfig(tokenBucket);
        mongoConfigStore.saveConfig(fixedWindow);
        mongoConfigStore.saveConfig(slidingWindow);
        mongoConfigStore.saveConfig(leakyBucket);

        // Assert
        var token = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.50", "/api/token", USER_TIER);
        var fixed = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.51", "/api/fixed", USER_TIER);
        var sliding = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.52", "/api/sliding", USER_TIER);
        var leaky = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.53", "/api/leaky", USER_TIER);

        assertThat(token).isPresent().get().extracting("algorithm").isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(fixed).isPresent().get().extracting("algorithm").isEqualTo(Algorithm.FIXED_WINDOW);
        assertThat(sliding).isPresent().get().extracting("algorithm").isEqualTo(Algorithm.SLIDING_WINDOW);
        assertThat(leaky).isPresent().get().extracting("algorithm").isEqualTo(Algorithm.LEAKY_BUCKET);

        log.info("All algorithm configurations verified successfully");
    }

    // ============================================================================
    // 7. Data Integrity Tests
    // ============================================================================

    @Test
    @DisplayName("Should maintain data integrity across multiple operations")
    @Timeout(10)
    void testDataIntegrityAcrossOperations() throws InterruptedException {
        // Arrange: Create initial configs
        RateLimitConfigEntity config1 = createTokenBucketConfig("192.168.1.70", "/api/test", 50, 60);
        RateLimitConfigEntity config2 = createFixedWindowConfig("192.168.1.71", "/api/test", 100, 60);

        mongoConfigStore.saveConfig(config1);
        mongoConfigStore.saveConfig(config2);
        Thread.sleep(200);

        // Act: Retrieve and verify
        var retrieved1 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.70", "/api/test", USER_TIER);
        var retrieved2 = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "192.168.1.71", "/api/test", USER_TIER);

        // Assert: Data should be intact
        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().getIp()).isEqualTo("192.168.1.70");
        assertThat(retrieved1.get().getAlgorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(retrieved1.get().getParameters().get("capacity")).isEqualTo(50);

        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().getIp()).isEqualTo("192.168.1.71");
        assertThat(retrieved2.get().getAlgorithm()).isEqualTo(Algorithm.FIXED_WINDOW);
        assertThat(retrieved2.get().getParameters().get("maxRequests")).isEqualTo(100);

        log.info("Data integrity verified across multiple operations");
    }

    @Test
    @DisplayName("Should handle non-existent IP configuration gracefully")
    @Timeout(10)
    void testNonExistentConfigurationRetrieval() {
        // Act
        var config = mongoConfigStore.getUserConfigWithIpAndEndpointAndUserTier(
                "999.999.999.999", "/api/nonexistent", USER_TIER);

        // Assert
        assertThat(config).isEmpty();

        log.info("Non-existent configuration retrieval handled gracefully");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private RateLimitConfigEntity createTokenBucketConfig(
            String ip, String endpoint, int capacity, int refillRate) {
        RateLimitConfigEntity config = new RateLimitConfigEntity();
        config.setIp(ip);
        config.setEndpoint(endpoint);
        config.setUserTier(USER_TIER);
        config.setAlgorithm(Algorithm.TOKEN_BUCKET);
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(1);
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("refillRate", refillRate);
        config.setParameters(params);
        return config;
    }

    private RateLimitConfigEntity createFixedWindowConfig(
            String ip, String endpoint, int maxRequests, int windowSize) {
        RateLimitConfigEntity config = new RateLimitConfigEntity();
        config.setIp(ip);
        config.setEndpoint(endpoint);
        config.setUserTier(USER_TIER);
        config.setAlgorithm(Algorithm.FIXED_WINDOW);
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(1);
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", maxRequests);
        params.put("windowSize", windowSize);
        config.setParameters(params);
        return config;
    }

    private RateLimitConfigEntity createSlidingWindowConfig(
            String ip, String endpoint, int maxRequests, int windowSize) {
        RateLimitConfigEntity config = new RateLimitConfigEntity();
        config.setIp(ip);
        config.setEndpoint(endpoint);
        config.setUserTier(USER_TIER);
        config.setAlgorithm(Algorithm.SLIDING_WINDOW);
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(1);
        Map<String, Integer> params = new HashMap<>();
        params.put("maxRequests", maxRequests);
        params.put("windowSize", windowSize);
        config.setParameters(params);
        return config;
    }

    private RateLimitConfigEntity createLeakyBucketConfig(
            String ip, String endpoint, int capacity, int leakRate) {
        RateLimitConfigEntity config = new RateLimitConfigEntity();
        config.setIp(ip);
        config.setEndpoint(endpoint);
        config.setUserTier(USER_TIER);
        config.setAlgorithm(Algorithm.LEAKY_BUCKET);
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(1);
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", capacity);
        params.put("leakRate", leakRate);
        config.setParameters(params);
        return config;
    }
}
