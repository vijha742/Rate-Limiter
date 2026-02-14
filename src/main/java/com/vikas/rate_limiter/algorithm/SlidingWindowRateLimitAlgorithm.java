package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import java.time.Clock;
import java.util.ArrayDeque;

@Data
public class SlidingWindowRateLimitAlgorithm implements RateLimitAlgorithm {

    private final Clock clock;
    private final int maxRequests;
    private final int windowLength;
    private ArrayDeque<Long> reqStorage = new ArrayDeque<>();

    public synchronized boolean acceptRequest() {
        long currentTime = this.clock.millis();
        long startWindow = currentTime - this.windowLength * 1000;
        int reqCount = 0;
        while (this.reqStorage.size() > 0 && this.reqStorage.getFirst() < startWindow) {
            this.reqStorage.remove();
        }
        reqCount = this.reqStorage.size();
        if (reqCount < this.maxRequests) {
            this.reqStorage.add(currentTime);
            return true;
        } else
            return false;
    }

    @Override
    public int getLimit() {
        return maxRequests;
    }

    @Override
    public synchronized int getRemainingRequests() {
        long currentTime = this.clock.millis();
        long startWindow = currentTime - this.windowLength * 1000;
        int reqCount = 0;
        while (this.reqStorage.size() > 0 && this.reqStorage.getFirst() < startWindow) {
            this.reqStorage.remove();
        }
        reqCount = this.reqStorage.size();
        return this.maxRequests - reqCount;
    }

    @Override
    public long resetTime() {
        return this.clock.millis() + 1000;
    }
}
