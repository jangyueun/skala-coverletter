package com.team.careerfit.job.exception;

import org.springframework.http.HttpStatus;

public class JobException extends RuntimeException {

    private final HttpStatus status;

    private JobException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static JobException invalidListQuery() {
        return new JobException(HttpStatus.BAD_REQUEST, "공고 조회 조건이 올바르지 않습니다.");
    }

    public HttpStatus status() {
        return status;
    }
}
