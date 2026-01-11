package com.vikas.rate_limiter.algorithm;

import lombok.Data;

@Data
public class LeakyBucketRateLimitAlgorithm implements RateLimitAlgorithm {

        private final int processRate;
        private int counter = 0;
        private final int maxCapacity;
        private long lastUpdateTime = System.currentTimeMillis();

        public synchronized boolean acceptRequest() {
                long currentTime = System.currentTimeMillis();
                this.counter = (int) Math.max(
                                0,
                                this.counter
                                                - (currentTime - this.lastUpdateTime)
                                                                / 1000
                                                                * this.processRate);
                if (this.counter < this.maxCapacity) {
                        counter += 1;
                        this.lastUpdateTime = currentTime;
                        return true;
                } else
                        return false;
        }
}
