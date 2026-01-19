package com.vikas.rate_limiter.dto;

import lombok.Data;

@Data
public class RateLimitDecision {
    private boolean allowed;
    private int limit;
    private int remaining;
    private long resetOn;
}
