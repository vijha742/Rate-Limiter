package com.vikas.rate_limiter.filter;

import com.vikas.rate_limiter.RateLimitManager;
import com.vikas.rate_limiter.dto.RateLimitDecision;
import com.vikas.rate_limiter.utils.IpUtil;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateFilter extends OncePerRequestFilter {
        private final RateLimitManager manager;
        private final IpUtil ipUtility;
        private final Counter blockedRequestsCounter;
        private final Counter allowedRequestsCounter;
        private final MeterRegistry registry;
        private final ConcurrentHashMap<String, Counter> endpointCounters = new ConcurrentHashMap<>();
        private final Timer requestsTimer;

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                return path.startsWith("/actuator")
                                || path.startsWith("/swagger")
                                || path.startsWith("/v3/api-docs")
                                || path.startsWith("/api/config/v2");
        }

        private void incrementEndpointRequestsCounter(String endpoint, boolean allowed) {
                Counter counter = endpointCounters.computeIfAbsent(
                                endpoint,
                                uri -> Counter.builder("ratelimiter.endpoint.requests.total")
                                                .description(
                                                                "Total number of requests received per endpoint")
                                                .tag("endpoint", uri)
                                                .tag("allowed", allowed ? "allowed" : "blocked")
                                                .register(registry));
                counter.increment();
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest req, HttpServletResponse res, FilterChain filterChain)
                        throws ServletException, IOException {
                Long time = System.nanoTime();
                String ip = ipUtility.getUserIp(req);
                String endpoint = req.getRequestURI();

                log.info("Request received from IP {} for uri {}", ip, endpoint);

                RateLimitDecision decision = manager.evaluateRequest(ip, endpoint);
                // HACK: Headers don't seem appropriate...
                res.setHeader("X-RateLimit-Limit", String.valueOf(decision.getLimit()));
                res.setHeader("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()));
                res.setHeader("X-RateLimit-ResetOn", String.valueOf(decision.getResetOn()));
                Long duration = System.nanoTime() - time;
                requestsTimer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                if (decision.isAllowed()) {
                        incrementEndpointRequestsCounter(endpoint, true);
                        allowedRequestsCounter.increment();
                        filterChain.doFilter(req, res);
                } else {
                        incrementEndpointRequestsCounter(endpoint, false);
                        blockedRequestsCounter.increment();
                        res.setStatus(429);
                        res.setContentType("text/plain");
                        PrintWriter writer = res.getWriter();
                        writer.write("All requests have been exhausted. Try again later!!!");
                }
        }
}
