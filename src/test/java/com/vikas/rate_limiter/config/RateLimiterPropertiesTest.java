package com.vikas.rate_limiter.config;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;
import com.vikas.rate_limiter.utils.RateLimiterProperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RateLimiterPropertiesTest {

    @Autowired
    private RateLimiterProperties properties;

    @Test
    void testYamlPropertiesLoaded() {
        assertNotNull(properties, "RateLimiterProperties should be loaded");
    }

    @Test
    void testFallbackAlgorithm() {
        assertNotNull(properties.getFallback(), "Fallback should not be null");
        assertEquals(Algorithm.TOKEN_BUCKET, properties.getFallback().getAlgorithm(),
                "Fallback algorithm should be TOKEN_BUCKET");
    }

    @Test
    void testLimitsConfiguration() {
        assertNotNull(properties.getLimits(), "Limits should not be null");
        assertEquals(10, properties.getLimits().getDefaultCapacity(),
                "Default capacity should be 10");
        assertEquals(1, properties.getLimits().getRefillRate(),
                "Refill rate should be 1");
    }

    @Test
    void testRedisConfiguration() {
        assertNotNull(properties.getRedis(), "Redis config should not be null");
        assertEquals(600000L, properties.getRedis().getTtl(),
                "Redis TTL should be 600000");
    }

    @Test
    void testSecurityConfiguration() {
        assertNotNull(properties.getSecurity(), "Security config should not be null");
        assertTrue(properties.getSecurity().isEnabled(),
                "Security should be enabled");
        assertTrue(properties.getSecurity().isLogSuspicious(),
                "Log suspicious should be enabled");
        assertNotNull(properties.getSecurity().getTrustedProxies(),
                "Trusted proxies should not be null");
        assertTrue(properties.getSecurity().getTrustedProxies().contains("127.0.0.1"),
                "Should contain localhost as trusted proxy");
    }

    @Test
    void testEndpointsConfiguration() {
        assertNotNull(properties.getEndpoints(), "Endpoints should not be null");
        assertEquals("/api/**", properties.getEndpoints().getPath(),
                "Endpoint path should be /api/**");
    }
}
