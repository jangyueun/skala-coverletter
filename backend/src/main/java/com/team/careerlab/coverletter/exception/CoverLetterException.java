package com.team.careerlab.coverletter.exception;

import com.team.careerlab.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CoverLetterException extends ApiException {

    private CoverLetterException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CoverLetterException questionNotFound() {
        return new CoverLetterException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "문항을 찾을 수 없습니다.");
    }

    public static CoverLetterException forbidden() {
        return new CoverLetterException(HttpStatus.FORBIDDEN, "FORBIDDEN", "다른 사용자의 경험을 근거로 쓸 수 없습니다.");
    }
}
