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

    public static ExperienceException fileTooLarge() {
        return new ExperienceException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                "첨부파일이 너무 큽니다. 파일당 10MB까지 올릴 수 있습니다.");
    }
}
