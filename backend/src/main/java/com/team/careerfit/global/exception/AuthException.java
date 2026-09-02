package com.team.careerfit.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 로그인 실패.
 *
 * <p><b>사용자에게 보내는 메시지에 내부 사정을 담지 않는다.</b> "Slack 이 invalid_grant 를
 * 줬다", "T012ABC 워크스페이스는 허용되지 않는다" 같은 문구는 로그에만 남긴다. 응답에
 * 담으면 공격자에게 어디까지 맞았는지 알려주는 셈이고, 정상 사용자는 그 정보로 할 수
 * 있는 일이 없다.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    private AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 인가 코드·state·토큰 교환 등 로그인 과정의 실패를 한 가지 메시지로 뭉뚱그린다. */
    public static AuthException loginFailed() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Slack 로그인에 실패했습니다.");
    }

    /** 다른 워크스페이스 계정. 어느 워크스페이스였는지는 응답에 담지 않는다. */
    public static AuthException workspaceNotAllowed() {
        return new AuthException(HttpStatus.FORBIDDEN, "허용되지 않은 Slack 워크스페이스입니다.");
    }

    public static AuthException loginRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }

    public HttpStatus status() {
        return status;
    }
}
