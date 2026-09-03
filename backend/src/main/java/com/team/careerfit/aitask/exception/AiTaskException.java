package com.team.careerfit.aitask.exception;

import com.team.careerfit.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AiTaskException extends ApiException {

    private AiTaskException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** 같은 사용자에게 이미 진행 중(PENDING·RUNNING)인 인테이크 작업이 있는데, 입력이 다를 때. */
    public static AiTaskException intakeAlreadyRunning() {
        return new AiTaskException(HttpStatus.CONFLICT, "INTAKE_ALREADY_RUNNING", "이미 진행 중인 인테이크 작업이 있습니다.");
    }
}
