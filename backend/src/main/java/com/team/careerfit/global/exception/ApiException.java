package com.team.careerfit.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인 예외 공통 베이스. 상태 코드와 함께 API 명세(docs/api-spec-v6.md)의 오류 {@code code} 를 들고 있다.
 *
 * <p>{@link AuthException} 은 이미 리뷰된 {@code {"message": ...}} 계약을 그대로 두기 위해
 * 여기 합류시키지 않는다 — 4개 인증 API 는 지금 형식대로 둔다.
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
