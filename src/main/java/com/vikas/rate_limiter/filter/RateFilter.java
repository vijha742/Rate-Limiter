package com.vikas.rate_limiter.filter;

import com.vikas.rate_limiter.RateLimitManager;
import com.vikas.rate_limiter.utils.IpUtil;

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
    private final RateLimitManager manager;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = IpUtil.getUserIp(req);
        if (manager.allowRequest(ip)) {
            filterChain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setContentType("text/plain");
            PrintWriter writer = res.getWriter();
            writer.write("All requests have been exhausted. Try again later!!!");
        }
    }
}
