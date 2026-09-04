package com.team.careerlab.integration.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python AI 서버 접속 설정.
 *
 * @param baseUrl AI 서버 루트 주소. 로컬 개발은 {@code http://localhost:8000}, compose 는 {@code http://ai:8000}
 * @param internalToken {@code Authorization: Bearer} 로 실어 보낼 내부 토큰. 비어 있으면 헤더를 안 붙인다 —
 *     AI 서버도 이 값이 비어 있으면 인증을 검사하지 않는다(로컬 개발 전용)
 * @param readTimeout 응답을 기다리는 상한. 인테이크는 AI 서버가 web_fetch 로 자료를 여러 번 읽어 몇 분이 걸릴 수 있다.
 *     워커가 백그라운드에서 기다리므로 길어도 화면은 안 멈춘다. AI 서버의 AI_REQUEST_TIMEOUT_SECONDS 보다 길게 둔다
 */
@ConfigurationProperties("careerlab.ai")
public record AiProviderProperties(String baseUrl, String internalToken, Duration readTimeout) {

    public AiProviderProperties {
        if (readTimeout == null) {
            readTimeout = Duration.ofMinutes(5);
        }
    }
}
