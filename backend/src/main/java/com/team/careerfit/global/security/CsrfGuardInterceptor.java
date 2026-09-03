package com.team.careerfit.global.security;

import com.team.careerfit.global.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 뒤의 상태 변경 요청(POST · PUT · PATCH · DELETE)에 대한 CSRF 방어.
 *
 * <p>브라우저가 붙이는 <b>Fetch Metadata</b>({@code Sec-Fetch-Site}) 헤더를 본다. 다른 사이트의 페이지가 우리
 * 사용자의 브라우저로 요청을 쏘면 브라우저가 {@code cross-site} 를 붙이고, 이 값은 페이지의 스크립트가
 * 바꿀 수 없다. 그 요청만 거부한다. {@code same-origin} · {@code same-site} · {@code none}(주소창 입력)은 통과다.
 *
 * <p>토큰 방식을 안 쓴 이유 — 토큰은 프론트가 요청마다 실어야 하고, 팀원들이 이미 써 둔 MockMvc 테스트 수십 개가
 * 전부 헤더를 붙이도록 고쳐야 한다. Fetch Metadata 는 프론트 코드 한 줄 없이 브라우저가 알아서 붙인다.
 *
 * <p>한계 — 이 헤더가 없는 옛 브라우저(2023년 이전 Safari)에서는 세션 쿠키의 {@code SameSite=Lax}
 * (application.yml)만 남는다. Lax 는 교차 사이트 POST 에 쿠키를 싣지 않으므로 그것만으로도 막히지만,
 * 방어를 한 겹 더 두는 게 이 인터셉터다. GET 은 보지 않는다 — Slack 콜백이 교차 사이트 GET 이고,
 * 우리 GET 은 상태를 바꾸지 않는다.
 */
public class CsrfGuardInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CsrfGuardInterceptor.class);

    static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String CROSS_SITE = "cross-site";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String site = request.getHeader(SEC_FETCH_SITE);
        if (site != null && CROSS_SITE.equalsIgnoreCase(site.trim())) {
            // 어느 사이트였는지(Origin)는 로그에만. 응답에 담을 이유가 없다.
            log.warn("교차 사이트 상태 변경 요청을 거부: {} {} origin={}",
                    request.getMethod(), request.getRequestURI(), request.getHeader("Origin"));
            throw AuthException.csrfRejected();
        }
        return true;
    }
}
