package com.team.careerlab.auth.service;

import com.team.careerlab.auth.dto.SlackApiResponses.Token;
import com.team.careerlab.auth.dto.SlackApiResponses.UserInfo;
import com.team.careerlab.auth.dto.SlackProfile;
import com.team.careerlab.global.config.AuthProperties;
import com.team.careerlab.global.exception.AuthException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Slack 과의 OpenID Connect 왕복.
 *
 * <p>흔히 쓰는 {@code oauth.v2.access}(봇 설치용)가 아니라 <b>OpenID Connect</b> 엔드포인트를
 * 쓴다. 우리는 봇을 설치하는 게 아니라 사람을 로그인시키는 것이고, 봇 토큰이나
 * {@code chat:write} 를 받을 이유가 없다. 받아 두면 유출됐을 때 피해만 커진다.
 *
 * <p>액세스 토큰은 밖으로 내보내지 않는다. 여기서 받아 userInfo 호출에만 쓰고 버린다.
 * DB 에도 저장하지 않는다 — 저장하면 그 순간부터 관리 대상이 된다.
 */
@Component
public class SlackOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SlackOAuthClient.class);

    private static final String AUTHORIZE_URL = "https://slack.com/openid/connect/authorize";
    private static final String TOKEN_URL = "https://slack.com/api/openid.connect.token";
    private static final String USERINFO_URL = "https://slack.com/api/openid.connect.userInfo";
    private static final String SCOPE = "openid profile email";

    private final RestClient restClient;
    private final AuthProperties properties;

    public SlackOAuthClient(AuthProperties properties) {
        // RestClient.Builder 빈을 주입받지 않고 직접 만든다.
        //
        // Spring Boot 4 는 RestClient 자동 설정을 starter-web 에서 떼어 별도 모듈
        // (spring-boot-starter-restclient)로 옮겼다. Builder 를 주입받게 짜면 그 의존성이
        // 없는 순간 "RestClient.Builder 빈 없음" 으로 기동 자체가 실패한다. build.gradle
        // 을 각자 만드는 상황이라 의존성 하나에 기동이 걸리지 않게 정적 팩토리를 쓴다.
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    /**
     * 사용자를 보낼 Slack 인가 URL.
     *
     * @param state CSRF 방어용 난수. 콜백에서 되돌아온 값과 대조한다
     * @param codeChallenge PKCE 챌린지(S256)
     */
    public String authorizationUrl(String state, String codeChallenge) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(properties.clientId())
                + "&scope=" + encode(SCOPE)
                // 설정값을 그대로 쓴다. 요청 헤더(Host)에서 유추하면 헤더를 위조해
                // 인가 코드를 공격자 서버로 보내게 만들 수 있다.
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256"
                // team 을 지정하면 Slack 이 그 워크스페이스 로그인 화면을 먼저 보여준다.
                // 편의일 뿐 보안 수단이 아니다 — 콜백에서의 team_id 검증을 대신하지 못한다.
                + "&team=" + encode(properties.allowedTeamId());
    }

    /** 인가 코드를 프로필로 바꾼다. */
    public SlackProfile exchange(String code, String codeVerifier) {
        return requestUserInfo(requestAccessToken(code, codeVerifier));
    }

    private String requestAccessToken(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        // 인가 요청 때와 정확히 같은 값이어야 한다. 다르면 Slack 이 거부한다.
        form.add("redirect_uri", properties.redirectUri());
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code_verifier", codeVerifier);

        Token token = restClient
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);

        if (token == null || !token.ok() || token.accessToken() == null) {
            log.warn("Slack 토큰 교환 실패: {}", token == null ? "응답 없음" : token.error());
            throw AuthException.loginFailed();
        }
        return token.accessToken();
    }

    private SlackProfile requestUserInfo(String accessToken) {
        UserInfo info = restClient
                .get()
                .uri(USERINFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(UserInfo.class);

        if (info == null || !info.ok()) {
            log.warn("Slack 사용자 정보 조회 실패: {}", info == null ? "응답 없음" : info.error());
            throw AuthException.loginFailed();
        }
        return new SlackProfile(
                info.teamId(),
                info.userId(),
                info.displayName(),
                info.realName(),
                info.email(),
                info.avatarUrl());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
