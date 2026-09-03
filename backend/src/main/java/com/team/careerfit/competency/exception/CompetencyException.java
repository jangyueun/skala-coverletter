package com.team.careerfit.competency.exception;

import org.springframework.http.HttpStatus;

public class CompetencyException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private CompetencyException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static CompetencyException invalidCategory() {
        return new CompetencyException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "역량 범주가 올바르지 않습니다.");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
