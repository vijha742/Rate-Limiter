package com.vikas.rate_limiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Rate Limiter system.
 * Tests the complete flow: Filter → Manager → Algorithm → Redis
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimiterIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    private static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        // Clear Redis before each test
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    /**
     * Test 1: Complete Request Flow
     * Validates: Filter → Manager → Algorithm → Redis
     */
    @Test
    void testCompleteRequestFlow() {
        // Configure a rate limit for the test endpoint
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(Algorithm.TOKEN_BUCKET);
        config.setEndpoint("/api/test");
        config.setMethod(HttpMethod.GET);
        
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", 5);
        params.put("refillRate", 1);
        params.put("refillPeriodSeconds", 60);
        config.setParameters(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestConfigDTO> configRequest = new HttpEntity<>(config, headers);

        // Store configuration
        ResponseEntity<Boolean> configResponse = restTemplate.postForEntity(
                "/api/config",
                configRequest,
                Boolean.class
        );
        assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configResponse.getBody()).isTrue();

        // Make requests to test endpoint
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/test", String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("Hello Vikas Jha...!");
            
            // Verify rate limit headers are present
            assertThat(response.getHeaders().get("X-RateLimit-Limit")).isNotNull();
            assertThat(response.getHeaders().get("X-RateLimit-Remaining")).isNotNull();
            assertThat(response.getHeaders().get("X-RateLimit-ResetOn")).isNotNull();
        }

        // 6th request should be rate limited
        ResponseEntity<String> rateLimitedResponse = restTemplate.getForEntity("/api/test", String.class);
        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Test 2: Configuration API
     * Tests the /config endpoint for dynamic rate limit configuration
     */
    @Test
    void testConfigurationAPI() {
        // Test with Fixed Window algorithm
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(Algorithm.FIXED_WINDOW);
        config.setEndpoint("/api/test/v2");
        config.setMethod(HttpMethod.GET);
        
        Map<String, Integer> params = new HashMap<>();
        params.put("limit", 10);
        params.put("windowSeconds", 60);
        config.setParameters(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestConfigDTO> request = new HttpEntity<>(config, headers);

        // POST configuration
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                "/api/config",
                request,
                Boolean.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();

        // Verify the configuration works by making a request
        ResponseEntity<String> testResponse = restTemplate.getForEntity("/api/test/v2", String.class);
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify headers show correct limit
        String limitHeader = testResponse.getHeaders().getFirst("X-RateLimit-Limit");
        assertThat(limitHeader).isEqualTo("10");
    }

    /**
     * Test 3: Rate Limit Headers
     * Validates all three rate limit headers are present and correctly formatted
     */
    @Test
    void testRateLimitHeaders() {
        // Configure rate limit
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(Algorithm.LEAKY_BUCKET);
        config.setEndpoint("/api/test");
        config.setMethod(HttpMethod.GET);
        
        Map<String, Integer> params = new HashMap<>();
        params.put("capacity", 3);
        params.put("leakRate", 1);
        params.put("leakPeriodSeconds", 30);
        config.setParameters(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestConfigDTO> configRequest = new HttpEntity<>(config, headers);

        restTemplate.postForEntity("/api/config", configRequest, Boolean.class);

        // Make first request
        ResponseEntity<String> response = restTemplate.getForEntity("/api/test", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify X-RateLimit-Limit header
        String limitHeader = response.getHeaders().getFirst("X-RateLimit-Limit");
        assertThat(limitHeader).isNotNull();
        assertThat(Integer.parseInt(limitHeader)).isEqualTo(3);
        
        // Verify X-RateLimit-Remaining header
        String remainingHeader = response.getHeaders().getFirst("X-RateLimit-Remaining");
        assertThat(remainingHeader).isNotNull();
        assertThat(Integer.parseInt(remainingHeader)).isLessThanOrEqualTo(3);
        
        // Verify X-RateLimit-ResetOn header
        String resetHeader = response.getHeaders().getFirst("X-RateLimit-ResetOn");
        assertThat(resetHeader).isNotNull();
        long resetTime = Long.parseLong(resetHeader);
        assertThat(resetTime).isGreaterThan(System.currentTimeMillis());
    }

    /**
     * Test 4: Switching Algorithms Per IP
     * Tests that different IPs can have different rate limiting algorithms
     */
    @Test
    void testSwitchingAlgorithmsPerIP() throws Exception {
        // Configure Token Bucket for first IP
        RequestConfigDTO tokenBucketConfig = new RequestConfigDTO();
        tokenBucketConfig.setAlgo(Algorithm.TOKEN_BUCKET);
        tokenBucketConfig.setEndpoint("/api/test");
        tokenBucketConfig.setMethod(HttpMethod.GET);
        
        Map<String, Integer> tokenParams = new HashMap<>();
        tokenParams.put("capacity", 2);
        tokenParams.put("refillRate", 1);
        tokenParams.put("refillPeriodSeconds", 60);
        tokenBucketConfig.setParameters(tokenParams);

        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.APPLICATION_JSON);
        headers1.set("X-Forwarded-For", "192.168.1.1");
        HttpEntity<RequestConfigDTO> request1 = new HttpEntity<>(tokenBucketConfig, headers1);

        ResponseEntity<Boolean> config1Response = restTemplate.postForEntity(
                "/api/config",
                request1,
                Boolean.class
        );
        assertThat(config1Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Configure Sliding Window for second IP
        RequestConfigDTO slidingWindowConfig = new RequestConfigDTO();
        slidingWindowConfig.setAlgo(Algorithm.SLIDING_WINDOW);
        slidingWindowConfig.setEndpoint("/api/test");
        slidingWindowConfig.setMethod(HttpMethod.GET);
        
        Map<String, Integer> slidingParams = new HashMap<>();
        slidingParams.put("limit", 5);
        slidingParams.put("windowSeconds", 60);
        slidingWindowConfig.setParameters(slidingParams);

        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.APPLICATION_JSON);
        headers2.set("X-Forwarded-For", "192.168.1.2");
        HttpEntity<RequestConfigDTO> request2 = new HttpEntity<>(slidingWindowConfig, headers2);

        ResponseEntity<Boolean> config2Response = restTemplate.postForEntity(
                "/api/config",
                request2,
                Boolean.class
        );
        assertThat(config2Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test first IP - should be limited at 2 requests
        for (int i = 0; i < 2; i++) {
            HttpHeaders testHeaders1 = new HttpHeaders();
            testHeaders1.set("X-Forwarded-For", "192.168.1.1");
            HttpEntity<Void> testRequest1 = new HttpEntity<>(testHeaders1);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/test",
                    HttpMethod.GET,
                    testRequest1,
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Third request from first IP should be rate limited
        HttpHeaders testHeaders1 = new HttpHeaders();
        testHeaders1.set("X-Forwarded-For", "192.168.1.1");
        HttpEntity<Void> testRequest1 = new HttpEntity<>(testHeaders1);
        
        ResponseEntity<String> limitedResponse = restTemplate.exchange(
                "/api/test",
                HttpMethod.GET,
                testRequest1,
                String.class
        );
        assertThat(limitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Test second IP - should allow 5 requests
        for (int i = 0; i < 5; i++) {
            HttpHeaders testHeaders2 = new HttpHeaders();
            testHeaders2.set("X-Forwarded-For", "192.168.1.2");
            HttpEntity<Void> testRequest2 = new HttpEntity<>(testHeaders2);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/test",
                    HttpMethod.GET,
                    testRequest2,
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            String limitHeader = response.getHeaders().getFirst("X-RateLimit-Limit");
            assertThat(limitHeader).isEqualTo("5");
        }
    }

    /**
     * Test 5: Concurrent Requests from Multiple IPs
     * Tests thread safety and proper isolation between different client IPs
     */
    @Test
    void testConcurrentRequestsFromMultipleIPs() throws InterruptedException {
        // Configure rate limit: 10 requests per IP
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(Algorithm.FIXED_WINDOW);
        config.setEndpoint("/api/test");
        config.setMethod(HttpMethod.GET);
        
        Map<String, Integer> params = new HashMap<>();
        params.put("limit", 10);
        params.put("windowSeconds", 60);
        config.setParameters(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestConfigDTO> configRequest = new HttpEntity<>(config, headers);

        restTemplate.postForEntity("/api/config", configRequest, Boolean.class);

        int numberOfIPs = 5;
        int requestsPerIP = 15;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfIPs * requestsPerIP);
        CountDownLatch latch = new CountDownLatch(numberOfIPs * requestsPerIP);

        // Track successful and rate-limited requests per IP
        Map<String, AtomicInteger> successCountPerIP = new HashMap<>();
        Map<String, AtomicInteger> rateLimitedCountPerIP = new HashMap<>();

        for (int ip = 1; ip <= numberOfIPs; ip++) {
            String ipAddress = "10.0.0." + ip;
            successCountPerIP.put(ipAddress, new AtomicInteger(0));
            rateLimitedCountPerIP.put(ipAddress, new AtomicInteger(0));

            for (int req = 0; req < requestsPerIP; req++) {
                final String currentIP = ipAddress;
                executorService.submit(() -> {
                    try {
                        HttpHeaders requestHeaders = new HttpHeaders();
                        requestHeaders.set("X-Forwarded-For", currentIP);
                        HttpEntity<Void> request = new HttpEntity<>(requestHeaders);

                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/test",
                                HttpMethod.GET,
                                request,
                                String.class
                        );

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCountPerIP.get(currentIP).incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            rateLimitedCountPerIP.get(currentIP).incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Handle any exceptions
                        System.err.println("Error during concurrent test: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        // Wait for all requests to complete
        latch.await();
        executorService.shutdown();

        // Verify each IP got exactly 10 successful requests and 5 rate-limited
        for (String ip : successCountPerIP.keySet()) {
            int successCount = successCountPerIP.get(ip).get();
            int rateLimitedCount = rateLimitedCountPerIP.get(ip).get();
            
            assertThat(successCount).isEqualTo(10);
            assertThat(rateLimitedCount).isEqualTo(5);
            assertThat(successCount + rateLimitedCount).isEqualTo(requestsPerIP);
        }
    }

    /**
     * Test 6: All Four Algorithms
     * Validates that all four algorithms work correctly in the integration flow
     */
    @Test
    void testAllAlgorithms() {
        Algorithm[] algorithms = {
            Algorithm.TOKEN_BUCKET,
            Algorithm.FIXED_WINDOW,
            Algorithm.LEAKY_BUCKET,
            Algorithm.SLIDING_WINDOW
        };

        for (Algorithm algorithm : algorithms) {
            // Clear Redis between algorithm tests
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands()
                    .flushAll();

            RequestConfigDTO config = new RequestConfigDTO();
            config.setAlgo(algorithm);
            config.setEndpoint("/api/test");
            config.setMethod(HttpMethod.GET);
            
            Map<String, Integer> params = new HashMap<>();
            
            // Set parameters based on algorithm
            switch (algorithm) {
                case TOKEN_BUCKET:
                    params.put("capacity", 3);
                    params.put("refillRate", 1);
                    params.put("refillPeriodSeconds", 60);
                    break;
                case FIXED_WINDOW:
                    params.put("limit", 3);
                    params.put("windowSeconds", 60);
                    break;
                case LEAKY_BUCKET:
                    params.put("capacity", 3);
                    params.put("leakRate", 1);
                    params.put("leakPeriodSeconds", 60);
                    break;
                case SLIDING_WINDOW:
                    params.put("limit", 3);
                    params.put("windowSeconds", 60);
                    break;
            }
            
            config.setParameters(params);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Forwarded-For", "172.16.0.1");
            HttpEntity<RequestConfigDTO> configRequest = new HttpEntity<>(config, headers);

            restTemplate.postForEntity("/api/config", configRequest, Boolean.class);

            // Make 3 requests - all should succeed
            for (int i = 0; i < 3; i++) {
                HttpHeaders testHeaders = new HttpHeaders();
                testHeaders.set("X-Forwarded-For", "172.16.0.1");
                HttpEntity<Void> testRequest = new HttpEntity<>(testHeaders);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        "/api/test",
                        HttpMethod.GET,
                        testRequest,
                        String.class
                );
                
                assertThat(response.getStatusCode())
                        .as("Algorithm " + algorithm + " should allow request " + (i + 1))
                        .isEqualTo(HttpStatus.OK);
            }

            // 4th request should be rate limited
            HttpHeaders testHeaders = new HttpHeaders();
            testHeaders.set("X-Forwarded-For", "172.16.0.1");
            HttpEntity<Void> testRequest = new HttpEntity<>(testHeaders);
            
            ResponseEntity<String> rateLimitedResponse = restTemplate.exchange(
                    "/api/test",
                    HttpMethod.GET,
                    testRequest,
                    String.class
            );
            
            assertThat(rateLimitedResponse.getStatusCode())
                    .as("Algorithm " + algorithm + " should rate limit 4th request")
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /**
     * Test 7: Redis Persistence
     * Verifies that rate limit state is properly stored and retrieved from Redis
     */
    @Test
    void testRedisPersistence() {
        // Configure rate limit
        RequestConfigDTO config = new RequestConfigDTO();
        config.setAlgo(Algorithm.FIXED_WINDOW);
        config.setEndpoint("/api/test");
        config.setMethod(HttpMethod.GET);
        
        Map<String, Integer> params = new HashMap<>();
        params.put("limit", 5);
        params.put("windowSeconds", 60);
        config.setParameters(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestConfigDTO> configRequest = new HttpEntity<>(config, headers);

        restTemplate.postForEntity("/api/config", configRequest, Boolean.class);

        // Make 3 requests
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/test", String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Verify Redis has keys related to rate limiting
        assertThat(redisTemplate.keys("*")).isNotEmpty();
        
        // Make 2 more requests - should still work (total 5)
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/test", String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 6th request should be rate limited
        ResponseEntity<String> response = restTemplate.getForEntity("/api/test", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
