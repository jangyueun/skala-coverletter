package com.team.careerfit.experience.exception;

import com.team.careerfit.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ExperienceException extends ApiException {

    private ExperienceException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static ExperienceException validationFailed(String message) {
        return new ExperienceException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public static ExperienceException notFound() {
        return new ExperienceException(HttpStatus.NOT_FOUND, "EXPERIENCE_NOT_FOUND", "경험을 찾을 수 없습니다.");
    }

    public static ExperienceException forbidden() {
        return new ExperienceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "다른 사용자의 경험입니다.");
    }
}
