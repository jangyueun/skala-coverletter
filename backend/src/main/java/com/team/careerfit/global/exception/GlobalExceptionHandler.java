package com.team.careerfit.global.exception;

import com.team.careerfit.competency.exception.CompetencyException;
import com.team.careerfit.job.exception.JobException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 예외를 JSON 으로 바꾸는 단 하나의 자리. 도메인마다 advice 를 만들면 팀원 수만큼
 * 오류 응답 형식이 갈린다.
 *
 * <p>스택트레이스나 원인 예외를 응답에 담지 않는다. 그건 로그에만 남는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** auth 는 이미 리뷰된 {@code {"message": ...}} 계약을 그대로 둔다. */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handle(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handle(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    /** competency·job 도메인은 {@link ApiException} 이전에 만들어져 따로 code()·status() 를 들고 있다. */
    @ExceptionHandler(CompetencyException.class)
    public ResponseEntity<Map<String, String>> handle(CompetencyException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(JobException.class)
    public ResponseEntity<Map<String, String>> handle(JobException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "VALIDATION_FAILED", "message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_ERROR", "message", "서버 오류가 발생했습니다."));
    }
}
