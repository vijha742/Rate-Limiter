package com.vikas.rate_limiter.algorithm;

import lombok.Data;

@Data
public class FixedCounterRateLimitAlgorithm implements RateLimitAlgorithm {
    private int counter;
    private long window_start;
    private int window_length;
    private int maxRequests;

    public FixedCounterRateLimitAlgorithm(int maxRequests, int window_length) {
        this.maxRequests = maxRequests > 0 ? maxRequests : 10;
        this.counter = 0;
        this.window_start = System.currentTimeMillis();
        this.window_length = window_length > 1 ? window_length : 5;
    }

    @Override
    public synchronized boolean acceptRequest() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - window_start > window_length * 1000) {
            window_start = currentTime;
            counter = 1;
            return true;
        }
        if (counter < maxRequests) {
            counter++;
            return true;
        }
        return false;
    }
}
