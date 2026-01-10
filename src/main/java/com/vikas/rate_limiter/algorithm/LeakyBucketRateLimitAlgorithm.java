package com.vikas.rate_limiter.algorithm;

import lombok.Data;

@Data
public class LeakyBucketRateLimitAlgorithm implements RateLimitAlgorithm {

        private final int process_rate;
        private int counter = 0;
        private final int max_requests;
        private long last_update_time = System.currentTimeMillis();

        public synchronized boolean acceptRequest() {
                long current_time = System.currentTimeMillis();
                this.counter = (int) Math.max(
                                0,
                                this.counter
                                                - (current_time - this.last_update_time)
                                                                / 1000
                                                                * this.process_rate);
                if (this.counter < this.max_requests) {
                        counter += 1;
                        this.last_update_time = current_time;
                        return true;
                } else
                        return false;
        }
}
