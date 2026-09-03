package com.team.careerfit.coverletter.exception;

import com.team.careerfit.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CoverLetterException extends ApiException {

    private CoverLetterException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CoverLetterException questionNotFound() {
        return new CoverLetterException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "문항을 찾을 수 없습니다.");
    }
}
