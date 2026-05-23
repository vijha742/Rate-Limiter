package com.vikas.rate_limiter.errorHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;

@ControllerAdvice
@Slf4j
public class GlobalErrorHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(RedisConnectionFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<String> handleRedisConnectionFailureException(Exception e) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.setErrorType("REDIS_CONNECTIVITY_ERROR");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage("Redis connection failed: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJson(response));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleInvalidArgumentsException(
            MethodArgumentNotValidException exception) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.BAD_REQUEST);
        response.setErrorType("METHOD_ARGUMENT_NOT_VALID");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage("Invalid request parameters: " + exception.getBindingResult().getFieldErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJson(response));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleGenericErrors(Exception e) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.setErrorType("INTERNAL_SERVER_ERROR");
        response.setTimestamp(LocalDateTime.now().toString());
        response.setErrorMessage(e.getMessage());
        log.error("Internal Server Error: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJson(response));
    }

    private String toJson(ErrorResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error serializing error response", e);
            return "{\"errorMessage\": \"Error serializing response\"}";
        }
    }
}
