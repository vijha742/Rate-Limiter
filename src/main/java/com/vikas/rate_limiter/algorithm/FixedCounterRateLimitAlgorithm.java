package com.vikas.rate_limiter.algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class FixedCounterRateLimitAlgorithm implements RateLimitAlgorithm {
    private int counter;
    private long windowStart;
    private int windowLength;
    private int maxRequests;

    public FixedCounterRateLimitAlgorithm(int maxRequests, int windowLength) {
        this.maxRequests = maxRequests > 0 ? maxRequests : 10;
        this.counter = 0;
        this.windowStart = System.currentTimeMillis();
        this.windowLength = windowLength > 1 ? windowLength : 5;
    }

    @Override
    public synchronized boolean acceptRequest() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - windowStart > windowLength * 1000) {
            windowStart = currentTime;
            counter = 1;
            return true;
        } else {
            if (counter < maxRequests) {
                counter++;
                return true;
            } else return false;
        }
    }
}
