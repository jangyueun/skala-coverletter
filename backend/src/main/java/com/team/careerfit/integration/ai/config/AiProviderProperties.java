package com.team.careerfit.integration.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python AI 서버(현재는 Mock) 접속 설정.
 *
 * @param baseUrl AI 서버 루트 주소. 로컬 개발은 {@code http://localhost:8000}
 * @param internalToken {@code Authorization: Bearer} 로 실어 보낼 내부 토큰. 비어 있으면 헤더를 안 붙인다 —
 *     AI 서버도 이 값이 비어 있으면 인증을 검사하지 않는다(로컬 개발 전용)
 */
@ConfigurationProperties("careerfit.ai")
public record AiProviderProperties(String baseUrl, String internalToken) {
}
