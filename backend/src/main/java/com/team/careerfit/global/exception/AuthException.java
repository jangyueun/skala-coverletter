package com.team.careerfit.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 로그인·인가 실패. 응답은 다른 도메인과 같은 {@code {"code", "message"}} 다(docs/api-spec-v6.md 9절).
 *
 * <p><b>사용자에게 보내는 메시지에 내부 사정을 담지 않는다.</b> "Slack 이 invalid_grant 를
 * 줬다", "T012ABC 워크스페이스는 허용되지 않는다" 같은 문구는 로그에만 남긴다. 응답에
 * 담으면 공격자에게 어디까지 맞았는지 알려주는 셈이고, 정상 사용자는 그 정보로 할 수
 * 있는 일이 없다.
 */
public class AuthException extends ApiException {

    private AuthException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** 인가 코드·토큰 교환·프로필 조회 등 로그인 과정의 실패를 한 가지 메시지로 뭉뚱그린다. */
    public static AuthException loginFailed() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "Slack 로그인에 실패했습니다.");
    }

    /**
     * 콜백의 state 가 우리가 심어 둔 값과 다르거나, 핸드셰이크 쿠키가 만료됐다.
     * 로그인 CSRF 이거나 동의 화면을 10분 넘게 열어 둔 것이다 — 둘 다 다시 시도하면 된다.
     */
    public static AuthException stateMismatch() {
        return new AuthException(HttpStatus.BAD_REQUEST, "STATE_MISMATCH",
                "로그인 요청이 만료됐거나 올바르지 않습니다. 다시 시도해 주세요.");
    }

    /** 다른 워크스페이스 계정. 어느 워크스페이스였는지는 응답에 담지 않는다. */
    public static AuthException workspaceNotAllowed() {
        return new AuthException(HttpStatus.FORBIDDEN, "WORKSPACE_NOT_ALLOWED", "허용되지 않은 Slack 워크스페이스입니다.");
    }

    public static AuthException loginRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인이 필요합니다.");
    }

    /** 다른 사이트의 페이지가 로그인 사용자의 브라우저로 쏜 상태 변경 요청({@code CsrfGuardInterceptor}). */
    public static AuthException csrfRejected() {
        return new AuthException(HttpStatus.FORBIDDEN, "CSRF_REJECTED", "다른 사이트에서 보낸 요청은 처리하지 않습니다.");
    }
}
