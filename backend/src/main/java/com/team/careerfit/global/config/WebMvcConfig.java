package com.team.careerfit.global.config;

import com.team.careerfit.global.security.CsrfGuardInterceptor;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.global.security.SessionAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 어느 경로가 무엇으로 지켜지는지는 전부 여기 있다. 인터셉터 클래스는 경로를 모른다.
 *
 * <pre>
 *   /api/**          CSRF 가드(상태 변경 요청) → 세션 필수
 *   /api/auth/**     CSRF 가드만. 로그인 전에 부르는 경로라 세션을 요구할 수 없다
 *                    (me 는 200 + null, slack/start·callback 은 302, logout 은 204)
 *   /internal/**     둘 다 아님. 서버 간 호출이라 X-Internal-Token 으로 지킨다(InternalPostingAnalysisController)
 * </pre>
 *
 * <p>인터셉터를 {@code @Component} 로 두지 않고 여기서 만든다 — {@code @WebMvcTest} 슬라이스가 무엇을 스캔하는지에
 * 기대지 않고, 이 설정 하나만 import 하면 테스트에서도 같은 경로 규칙이 걸린다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUser currentUser;

    public WebMvcConfig(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // CSRF 가드가 먼저다 — DB 를 안 보므로 싸고, 거부할 요청에 사용자 조회를 낭비하지 않는다.
        registry.addInterceptor(new CsrfGuardInterceptor())
                .addPathPatterns("/api/**")
                .order(0);
        registry.addInterceptor(new SessionAuthInterceptor(currentUser))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(1);
    }
}
