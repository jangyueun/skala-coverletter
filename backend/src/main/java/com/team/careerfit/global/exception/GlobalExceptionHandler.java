package com.team.careerfit.global.exception;

import com.team.careerfit.competency.exception.CompetencyException;
import com.team.careerfit.internal.exception.InternalApiException;
import com.team.careerfit.job.exception.JobException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 JSON 으로 바꾸는 단 하나의 자리. 도메인마다 advice 를 만들면 팀원 수만큼
 * 오류 응답 형식이 갈린다.
 *
 * <p>스택트레이스나 원인 예외를 응답에 담지 않는다. 그건 로그에만 남는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@link AuthException} 도 여기로 온다 — 인터셉터({@code SessionAuthInterceptor} · {@code CsrfGuardInterceptor})가
     * 던진 것도 마찬가지다. 핸들러가 먼저 정해진 뒤 인터셉터가 돌기 때문에 advice 가 그대로 받는다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handle(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    /**
     * competency·job·internal 도메인은 {@link ApiException} 이전에 만들어져 따로 code()·status() 를
     * 들고 있다. 예전엔 internal 만 별도 {@code @RestControllerAdvice}(InternalApiExceptionHandler)를
     * 따로 뒀는데, 이 프로젝트에 advice 가 둘이면 Spring 이 advice 빈을 도는 순서(이름순으로
     * GlobalExceptionHandler 가 먼저 걸린다)에 따라 여기 {@link #handle(Exception)} 이 먼저 매치돼서
     * InternalApiException 이 항상 500 으로 새는 버그가 있었다 — 실제로 겪었다. 그래서 여기 하나로 합쳤다.
     */
    @ExceptionHandler(CompetencyException.class)
    public ResponseEntity<Map<String, String>> handle(CompetencyException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(JobException.class)
    public ResponseEntity<Map<String, String>> handle(JobException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(InternalApiException.class)
    public ResponseEntity<Map<String, String>> handle(InternalApiException e) {
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

    /**
     * 없는 경로·틀린 메서드. 아래 {@link #handle(Exception)} 이 이걸 500 으로 바꾸고 error 로그까지 남기고 있었다 —
     * 주소를 잘못 친 프론트 한 줄이 서버 장애처럼 보였다. Spring 이 이미 판정한 상태 코드를 그대로 돌려준다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handle(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "NOT_FOUND", "message", "요청한 경로가 없습니다."));
    }

    /** {@code ?type=WHAT} 처럼 enum·숫자 파라미터를 못 읽는 경우. 요청 값 문제라 400 이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handle(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "VALIDATION_FAILED", "message", "요청 파라미터 " + e.getName() + " 의 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handle(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("code", "METHOD_NOT_ALLOWED", "message", "이 경로는 " + e.getMethod() + " 를 받지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_ERROR", "message", "서버 오류가 발생했습니다."));
    }
}
