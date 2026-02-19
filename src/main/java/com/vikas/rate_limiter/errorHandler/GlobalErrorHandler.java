package com.vikas.rate_limiter.errorHandler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;

@ControllerAdvice
@Slf4j
public class GlobalErrorHandler {

    @ExceptionHandler(RedisConnectionFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<String> handleRedisConnectionFailureException(Exception e) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.setErrorType("REDIS CONNECTIVITY ERROR");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage(e.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Redis Connectivity Issue : " + response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleInvalidArgumentsException(
            MethodArgumentNotValidException exception) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.BAD_REQUEST);
        response.setErrorType("METHOD ERROR");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage(exception.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Method argument wasn't valid." + response);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleGenericErrors(Exception e) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.setErrorType("GENERIC ERROR");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage(e.toString());
        log.error("Internal Server Erroe : " + response);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Server Error : " + response);
    }
}
