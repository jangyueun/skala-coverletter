package com.team.careerfit.internal.exception;

import org.springframework.http.HttpStatus;

public class InternalApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private InternalApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static InternalApiException invalidToken() {
        return new InternalApiException(
                "INTERNAL_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "내부 API 토큰이 올바르지 않습니다.");
    }

    public static InternalApiException postingNotFound() {
        return new InternalApiException(
                "POSTING_NOT_FOUND", HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다.");
    }

    public static InternalApiException analysisAlreadyRunning() {
        return new InternalApiException(
                "ANALYSIS_ALREADY_RUNNING", HttpStatus.CONFLICT, "다른 입력의 공고 분석이 이미 진행 중입니다.");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
