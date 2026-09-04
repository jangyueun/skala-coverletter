package com.team.careerlab.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인 예외 공통 베이스. 상태 코드와 함께 API 명세(docs/api-spec-v6.md)의 오류 {@code code} 를 들고 있다.
 *
 * <p>{@link AuthException} 도 여기 합류했다. 처음엔 {@code {"message"}} 만 주던 인증 4개 API 를 그대로 두려고
 * 따로 뒀는데, 명세 9절이 {@code LOGIN_REQUIRED} · {@code STATE_MISMATCH} 같은 code 를 인증에도 정의하고 있고
 * 프론트는 message 만 읽으므로 code 를 더하는 건 깨지는 변경이 아니다.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
