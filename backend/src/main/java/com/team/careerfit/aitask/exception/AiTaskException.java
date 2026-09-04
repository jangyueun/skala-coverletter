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

    /** 같은 문항에 이미 진행 중(PENDING·RUNNING)인 초안 작업이 있는데, 근거 경험 선택이 달라졌을 때. */
    public static AiTaskException draftAlreadyRunning() {
        return new AiTaskException(HttpStatus.CONFLICT, "DRAFT_ALREADY_RUNNING", "이미 진행 중인 초안 작업이 있습니다.");
    }

    public static AiTaskException taskNotFound() {
        return new AiTaskException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "AI 작업을 찾을 수 없습니다.");
    }

    /** 남의 작업, 또는 사용자가 없는 내부 작업(공고 분석). 존재 여부를 숨기지 않는다 — id 는 순번이라 어차피 추측된다. */
    public static AiTaskException forbidden() {
        return new AiTaskException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 AI 작업을 볼 수 없습니다.");
    }
}
