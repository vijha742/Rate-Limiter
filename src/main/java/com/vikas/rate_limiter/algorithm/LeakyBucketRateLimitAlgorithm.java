package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import java.time.Clock;

@Data
public class LeakyBucketRateLimitAlgorithm {

        private final Clock clock;
        private final int processRate;
        private int counter = 0;
        private final int maxCapacity;
        private long lastUpdateTime;

        public LeakyBucketRateLimitAlgorithm(int processRate, int maxCapacity, Clock clock) {
                this.clock = clock;
                this.processRate = processRate;
                this.maxCapacity = maxCapacity;
                this.lastUpdateTime = clock.millis();
        }

        public synchronized boolean acceptRequest() {
                long currentTime = this.clock.millis();
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

        public int getLimit() {
                return this.maxCapacity;
        }

        public int getRemainingRequests() {
                return this.maxCapacity - this.counter;
        }

        public long resetTime() {
                return this.clock.millis() + 1000;
        }
}
