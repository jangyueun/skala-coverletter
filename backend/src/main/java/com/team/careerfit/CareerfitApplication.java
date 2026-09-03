package com.team.careerfit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 애플리케이션 진입점.
 *
 * <p>패키지는 {@code com.team.careerfit} — 도메인 패키지(auth · user · global …)의 부모여야
 * 컴포넌트 스캔이 아래를 전부 잡는다. 여기서 한 칸이라도 내려가면 스캔에서 빠진 빈이 생긴다.
 *
 * <p>{@code @ConfigurationPropertiesScan} 이 필요한 이유 — {@code AuthProperties} 는
 * {@code @ConfigurationProperties} 만 붙은 record 고 {@code @Component} 가 없다. 이 스캔이
 * 없으면 빈으로 등록되지 않아 {@code SlackOAuthClient} · {@code CurrentUser} 의 생성자 주입이
 * 실패하고 기동 자체가 깨진다. 지우지 말 것.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerfitApplication.class, args);
    }
}
