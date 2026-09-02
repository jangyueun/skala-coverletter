package com.team.careerfit.global.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 로그인 실패를 JSON 으로 바꾼다.
 *
 * <p>스택트레이스나 원인 예외를 응답에 담지 않는다. 그건 로그에만 남는다.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handle(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }
}
