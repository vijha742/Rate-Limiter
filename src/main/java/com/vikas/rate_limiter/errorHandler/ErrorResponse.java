package com.vikas.rate_limiter.errorHandler;

import lombok.Data;

import org.springframework.http.HttpStatus;

@Data
public class ErrorResponse {

    private HttpStatus errorCode;
    private String errorMessage;
    private String errorType;
    private String timestamp;

    public ErrorResponse() {
    }
}
