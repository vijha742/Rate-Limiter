package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SlidingWindowRateLimitAlgorithm implements RateLimitAlgorithm {

    private final Clock clock;
    private final int maxRequests;
    private final int windowLength;
    private Map<Long, Integer> reqStorage = new ConcurrentHashMap<>();

    public synchronized boolean acceptRequest() {
        long currentTime = clock.millis();
        long startWindow = currentTime - this.windowLength * 1000;
        int reqCount = 0;
        for (long val : reqStorage.keySet()) {
            if (val >= startWindow) {
                reqCount += this.reqStorage.get(val);
            } else if (val < startWindow - 5000) {
                this.reqStorage.remove(val);
            }
        }

        if (reqCount < this.maxRequests) {
            this.reqStorage.put(currentTime, this.reqStorage.getOrDefault(currentTime, 0) + 1);
            return true;
        } else return false;
    }
}
