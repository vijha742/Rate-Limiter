package com.vikas.rate_limiter.algorithm;

import lombok.Data;

import java.time.Clock;

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
public class TokenBucketRateLimitAlgorithm implements RateLimitAlgorithm {

    private final Clock clock;
    private final int requestFillRate;
    private final long startTime;
    private final int maxCapacity;
    private int tokensInBucket; // WARN: Why is this wrong and what can we do to set it up
    private long lastRequestTime;

    public TokenBucketRateLimitAlgorithm(int requestFillRate, int maxCapacity, Clock clock) {
        this.requestFillRate = requestFillRate;
        this.maxCapacity = maxCapacity;
        this.tokensInBucket = maxCapacity;
        this.clock = clock;
        this.startTime = clock.millis();
        this.lastRequestTime = clock.millis();
    }

    @Override
    public synchronized boolean acceptRequest() {
        long currentTime = this.clock.millis();
        this.tokensInBucket =
                (int)
                        Math.min(
                                this.maxCapacity,
                                ((currentTime - this.lastRequestTime) / 1000) * this.requestFillRate
                                        + this.tokensInBucket);

        if (this.tokensInBucket > 0) {
            this.tokensInBucket -= 1;
            this.lastRequestTime = currentTime;
            return true;
        } else return false;
    }
}
