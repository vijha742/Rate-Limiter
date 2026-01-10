package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SlidingWindowRateLimitAlgorithm implements RateLimitAlgorithm {
    private final int max_requests;
    private final int window_length;
    private Map<Long, Integer> req_storage = new ConcurrentHashMap<>();

    public boolean acceptRequest() {
        long current_time = System.currentTimeMillis();
        long start_window = current_time - this.window_length * 1000;
        int req_count = 0;
        for (long val : req_storage.keySet()) {
            if (val >= start_window) {
                req_count += this.req_storage.get(val);
            } else if (val < start_window - 5000) {
                this.req_storage.remove(val);
            }
        }

        if (req_count < this.max_requests) {
            this.req_storage.put(current_time, this.req_storage.getOrDefault(current_time, 0) + 1);
            return true;
        } else
            return false;
    }
}
