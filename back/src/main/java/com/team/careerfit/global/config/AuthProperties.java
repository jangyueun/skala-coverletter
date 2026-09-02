package com.team.careerfit.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Slack 로그인 설정.
 *
 * <p><b>client-secret 은 저장소에 두지 않는다.</b> application.yml 에는 환경변수 참조만
 * 있고 실제 값은 각자의 application-local.yml(.gitignore 됨) 이나 환경변수로 주입한다.
 *
 * @param clientId Slack App 의 Client ID
 * @param clientSecret Slack App 의 Client Secret — 절대 커밋 금지
 * @param allowedTeamId 로그인을 허용할 워크스페이스. 우리 워크스페이스는 T089ENT4A2D
 * @param redirectUri 콜백 주소. <b>요청 헤더에서 유추하지 않고 이 설정값을 그대로 쓴다.</b>
 *     유추하면 Host 헤더를 위조해 인가 코드를 다른 서버로 보낼 수 있다
 * @param cookieSecure 쿠키에 Secure 를 붙일지. 운영은 true, 로컬 http 개발은 false
 * @param sessionTtl 로그인 세션 수명
 */
@ConfigurationProperties("careerfit.auth")
public record AuthProperties(
        String clientId,
        String clientSecret,
        String allowedTeamId,
        String redirectUri,
        boolean cookieSecure,
        Duration sessionTtl) {

    public AuthProperties {
        if (sessionTtl == null) {
            sessionTtl = Duration.ofDays(14);
        }
    }
}
