package com.team.careerfit.internal.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InternalApiExceptionHandler {

    @ExceptionHandler(InternalApiException.class)
    public ResponseEntity<Map<String, String>> handle(InternalApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("code", exception.code(), "message", exception.getMessage()));
    }
}
