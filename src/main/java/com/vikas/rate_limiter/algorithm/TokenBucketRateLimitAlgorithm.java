package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import org.springframework.stereotype.Component;

// NOTE: Used @component so that it becomes a Singleton and is initialized at the
// application
// startup. Without this application was being triggered at the RateFilter level
// and was
// stateless as it was being triggered for each request...This was causing
// unexpected
// behaviour while testing...
// But the question is why it wasn't happening for FixedCoubnter and sliding
// window algorithm...They
// were working as usual...
@Data
@Component
public class TokenBucketRateLimitAlgorithm implements RateLimitAlgorithm {

    private final int request_fill_rate;
    private long start_time = System.currentTimeMillis();
    private final int max_capacity;
    private int tokens_in_bucket = 0; // WARN: Why is this wrong and what can we do to set t up
    private long last_request_time = System.currentTimeMillis();

    @Override
    public synchronized boolean acceptRequest() {
        long current_time = System.currentTimeMillis();
        this.tokens_in_bucket = (int) Math.min(
                this.max_capacity,
                ((current_time - this.last_request_time) / 1000)
                        * this.request_fill_rate
                        + this.tokens_in_bucket);

        if (this.tokens_in_bucket > 0) {
            this.tokens_in_bucket -= 1;
            return true;
        } else
            return false;
    }
}
