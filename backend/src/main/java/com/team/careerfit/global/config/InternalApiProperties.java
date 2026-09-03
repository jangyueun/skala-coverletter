package com.team.careerfit.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 서버 간 API 인증 설정. 실제 토큰은 저장소가 아닌 환경변수에서 주입한다. */
@ConfigurationProperties("careerfit.internal")
public record InternalApiProperties(String token) {
}
