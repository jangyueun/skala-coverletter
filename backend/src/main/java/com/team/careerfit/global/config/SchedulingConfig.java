package com.team.careerfit.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}(AiTaskWorker 등) 는 {@code test} 프로파일에서 끈다.
 *
 * <p>테스트는 커넥션 풀이 2개뿐인데(application.yml) 백그라운드 워커가 5초마다 같은 풀을 두고
 * 경쟁하면 테스트 요청이 커넥션을 못 얻어 무관한 테스트가 500 으로 실패한다 — 실제로 겪은 문제다.
 * `.env` import 를 {@code !test} 로 막는 것과 같은 이유·같은 패턴이다.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
