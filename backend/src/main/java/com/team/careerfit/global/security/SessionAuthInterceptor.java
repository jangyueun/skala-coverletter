package com.team.careerfit.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/**} 는 세션 필수(docs/api-spec-v6.md 전제). 어느 경로에 걸리는지는 {@code WebMvcConfig} 가 정한다 —
 * {@code /api/auth/**} 는 로그인 전에 부르는 것들이라 빠지고, {@code /internal/**} 은 세션이 아니라 토큰으로 지킨다.
 *
 * <p>컨트롤러마다 {@code currentUser.require(request)} 를 부르던 것을 한 곳으로 모은 것이다. 컨트롤러의 호출은
 * 그대로 둔다 — 사용자 객체가 필요해서고, {@link CurrentUser} 가 요청 속성에 캐시하므로 DB 는 한 번만 간다.
 * 새 컨트롤러가 {@code require()} 를 빠뜨려도 여기서 막힌다. 그게 이 인터셉터를 두는 이유다.
 *
 * <p>여기서 던진 {@code AuthException} 은 {@code GlobalExceptionHandler} 가 받아
 * 401 {@code LOGIN_REQUIRED} 로 바꾼다. 인터셉터는 핸들러가 정해진 뒤에 돌아서 advice 가 그대로 적용된다.
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;

    public SessionAuthInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        currentUser.require(request);
        return true;
    }
}
