package com.vikas.rate_limiter.filter;

import com.vikas.rate_limiter.RateLimitManager;
import com.vikas.rate_limiter.dto.RateLimitDecision;
import com.vikas.rate_limiter.utils.IpUtil;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class RateFilter extends OncePerRequestFilter {
    private final RateLimitManager manager;
    private final IpUtil ipUtility;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = ipUtility.getUserIp(req);
        log.info("Request recieved from IP {}", ip);
        RateLimitDecision decision = manager.evaluateRequest(ip, req.getRequestURI());
        res.setHeader("X-RateLimit-Limit", String.valueOf(decision.getLimit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()));
        res.setHeader("X-RateLimit-ResetOn", String.valueOf(decision.getResetOn()));
        if (decision.isAllowed()) {
            filterChain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setContentType("text/plain");
            PrintWriter writer = res.getWriter();
            writer.write("All requests have been exhausted. Try again later!!!");
        }
    }
}
