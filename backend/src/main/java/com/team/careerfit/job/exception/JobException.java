package com.team.careerfit.job.exception;

import org.springframework.http.HttpStatus;

public class JobException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private JobException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static JobException invalidListQuery() {
        return new JobException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "공고 조회 조건이 올바르지 않습니다.");
    }

    public static JobException postingNotFound() {
        return new JobException("POSTING_NOT_FOUND", HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다.");
    }

    public static JobException invalidBookmarkRequest() {
        return new JobException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "북마크 요청이 올바르지 않습니다.");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
