package com.vikas.rate_limiter.algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;

@Slf4j
@Data
public class FixedCounterRateLimitAlgorithm implements RateLimitAlgorithm {
    private final Clock clock;
    private int counter;
    private long windowStart;
    private int windowLength;
    private int maxRequests;

    public FixedCounterRateLimitAlgorithm(int maxRequests, int windowLength, Clock clock) {
        this.clock = clock;
        this.maxRequests = maxRequests > 0 ? maxRequests : 10;
        this.counter = 0;
        this.windowStart = clock.millis();
        this.windowLength = windowLength > 1 ? windowLength : 5;
    }

    @Override
    public synchronized boolean acceptRequest() {
        long currentTime = clock.millis();
        if (currentTime - windowStart > windowLength * 1000) {
            windowStart = currentTime;
            counter = 1;
            return true;
        } else {
            if (counter < maxRequests) {
                counter++;
                return true;
            } else
                return false;
        }
    }

    @Override
    public int getLimit() {
        return this.maxRequests;
    }

    @Override
    public int getRemainingRequests() {
        return this.maxRequests - this.counter;
    }

    @Override
    public long resetTime() {
        return this.windowStart + this.windowLength;
    }
}
