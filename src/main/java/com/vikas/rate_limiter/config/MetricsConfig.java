package com.vikas.rate_limiter.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

        @Bean
        public Counter blockedRequestsCounter(MeterRegistry registry) {
                return Counter.builder("ratelimiter.requests.blocked")
                                .description("Total number of blocked requests due to rate limiting")
                                .register(registry);
        }

        @Bean
        public Counter allowedRequestsCounter(MeterRegistry registry) {
                return Counter.builder("ratelimiter.requests.allowed")
                                .description("Total number of allowed requests")
                                .register(registry);
        }

        @Bean
        public Gauge requestsDecisionRatioGauge(
                        MeterRegistry registry,
                        Counter blockedRequestsCounter,
                        Counter allowedRequestsCounter) {
                return Gauge.builder(
                                "ratelimiter.requests.decision_ratio",
                                () -> {
                                        double blocked = blockedRequestsCounter.count();
                                        double allowed = allowedRequestsCounter.count();
                                        double total = blocked + allowed;
                                        return total > 0 ? blocked / total : 0.0;
                                })
                                .description("Ratio of blocked requests to total requests")
                                .register(registry);
        }

        @Bean
        public Timer requestsTimer(MeterRegistry registry) {
                return Timer.builder("rate_limiter.decision.latency")
                                .description("Time taken to decide allow/reject")
                                .publishPercentiles(0.5, 0.95, 0.99)
                                .publishPercentileHistogram()
                                .register(registry);
        }

        // @Bean
        // public Gauge activeIpCountGauge(MeterRegistry registry, IpTracker ipTracker)
        // {
        // return Gauge.builder("ratelimiter.active_ips", ipTracker,
        // IpTracker::getActiveIpCount)
        // .description("Number of active IPs being tracked for rate limiting")
        // .register(registry);
        // }

        // @Bean
        // public Counter repeatedViolationsCounter(MeterRegistry registry) {
        // return Counter.builder("ratelimiter.ip.repeated_violations")
        // .description("Number of IPs that have repeatedly violated rate limits")
        // .register(registry);
        // }

        // REMOVED: This counter is now dynamically registered in RateFilter with
        // endpoint tag
        // Having both a bean and dynamic registration with different tag sets causes
        // Prometheus
        // conflicts
        // @Bean
        // public Counter totalEndpointRequestsCounter(MeterRegistry registry) {
        // return Counter.builder("ratelimiter.endpoint.requests.total")
        // .description("Total number of requests received per endpoint")
        // .register(registry);
        // }

        // @Bean
        // public Gauge endpointRateLimitedPercentageGauge(MeterRegistry registry,
        // EndpointTracker endpointTracker) {
        // return Gauge.builder("ratelimiter.endpoint.rate_limited_percentage",
        // endpointTracker, EndpointTracker::getRateLimitedPercentage)
        // .description("Percentage of requests that were rate limited per endpoint")
        // .register(registry);
        // }

        @Bean
        public Counter cacheHitsCounter(MeterRegistry registry) {
                return Counter.builder("ratelimiter.cache.hits")
                                .description("Number of times the rate limit decision was served from cache")
                                .register(registry);
        }

        @Bean
        public Counter cacheMissesCounter(MeterRegistry registry) {
                return Counter.builder("ratelimiter.cache.misses")
                                .description(
                                                "Number of times the rate limit decision was not found in cache and had to"
                                                                + " be computed")
                                .register(registry);
        }

        // @Bean
        // public Gauge configFetchDurationGauge(MeterRegistry registry,
        // ConfigFetchTimer configFetchTimer) {
        // return Gauge.builder("ratelimiter.db.config_fetch_duration",
        // configFetchTimer, ConfigFetchTimer::getAverageDuration)
        // .description("Average duration taken to fetch rate limit configuration from
        // the database")
        // .register(registry);
        // }

        // @Bean
        // public Gauge redisOperationDurationGauge(MeterRegistry registry,
        // RedisOperationTimer redisOperationTimer) {
        // return Gauge.builder("ratelimiter.redis.operation_duration",
        // redisOperationTimer, RedisOperationTimer::getAverageDuration)
        // .description("Average duration taken for Redis operations related to rate
        // limiting")
        // .register(registry);
        // }

        // @Bean
        // public Gauge freeTierUsagePercentageGauge(MeterRegistry registry,
        // TierUsageTracker tierUsageTracker) {
        // return Gauge.builder("ratelimiter.tier.free.usage_percentage",
        // tierUsageTracker, TierUsageTracker::getFreeTierUsagePercentage)
        // .description("Percentage of rate limit usage for free tier users")
        // .register(registry);
        // }

        // @Bean
        // public Gauge premiumTierUsagePercentageGauge(MeterRegistry registry,
        // TierUsageTracker tierUsageTracker) {
        // return Gauge.builder("ratelimiter.tier.premium.usage_percentage",
        // tierUsageTracker, TierUsageTracker::getPremiumTierUsagePercentage)
        // .description("Percentage of rate limit usage for premium tier users")
        // .register(registry);
        // }

        // @Bean
        // public Gauge decisionLatencyGauge(MeterRegistry registry,
        // DecisionLatencyTracker decisionLatencyTracker) {
        // return Gauge.builder("ratelimiter.decision.latency", decisionLatencyTracker,
        // DecisionLatencyTracker::getAverageLatency)
        // .description("Average latency for making rate limit decisions")
        // .register(registry);
        // }

        // @Bean
        // public Gauge algorithmExecutionDurationGauge(MeterRegistry registry,
        // AlgorithmExecutionTimer algorithmExecutionTimer) {
        // return Gauge.builder("ratelimiter.algorithm.execution_duration",
        // algorithmExecutionTimer, AlgorithmExecutionTimer::getAverageDuration)
        // .description("Average duration taken to execute the rate limiting algorithm")
        // .register(registry);
        // }
}
