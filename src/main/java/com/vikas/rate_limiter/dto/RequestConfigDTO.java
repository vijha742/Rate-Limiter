package com.vikas.rate_limiter.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RequestConfigDTO {
    private String ip;
    private Algorithm algo;
    private Map<String, Integer> parameters;

    public enum Algorithm {
        TOKEN_BUCKET,
        FIXED_WINDOW,
        LEAKY_BUCKET,
        SLIDING_WINDOW
    }
}
