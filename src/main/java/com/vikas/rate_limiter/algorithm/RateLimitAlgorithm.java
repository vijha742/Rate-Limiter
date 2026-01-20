package com.vikas.rate_limiter.algorithm;

public interface RateLimitAlgorithm { // public type name
    boolean acceptRequest();

    int getLimit();

    int getRemainingRequests();

    long resetTime();
}
