package com.vikas.rate_limiter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.Map;

@Data
public class RequestConfigDTO {
    private String ip;

    @NotNull(message = "Algorithm cannot be null")
    private Algorithm algo;

    @NotNull(message = "Parameters cannot be null")
    @Size(min = 1, message = "At least one parameter must be provided")
    private Map<String, Integer> parameters;

    public enum Algorithm {
        TOKEN_BUCKET,
        FIXED_WINDOW,
        LEAKY_BUCKET,
        SLIDING_WINDOW
    }
}
