package com.vikas.rate_limiter.filter;

import com.vikas.rate_limiter.algorithm.LeakyBucketRateLimitAlgorithm;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Component
@RequiredArgsConstructor
public class RateFilter extends OncePerRequestFilter {
    private final LeakyBucketRateLimitAlgorithm algo;

    // this.algo = new FixedCounterRateLimitAlgorithm(10, 5);
    // this.algo = new SlidingWindowRateLimitAlgorithm(10, 5);
    // this.algo = new TokenBucketRateLimitAlgorithm(2, 10);

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain filterChain)
            throws ServletException, IOException {
        if (this.algo.acceptRequest()) {
            filterChain.doFilter(req, res);
        } else {
            res.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
            res.setContentType("text/plain");
            PrintWriter writer = res.getWriter();
            writer.write("All requests have been exhausted. Try again later!!!");
        }
    }
}
