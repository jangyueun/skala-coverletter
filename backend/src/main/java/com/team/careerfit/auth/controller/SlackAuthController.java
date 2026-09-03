package com.team.careerfit.auth.controller;

import com.team.careerfit.auth.service.SlackLoginService;
import com.team.careerfit.global.config.AuthProperties;
import com.team.careerfit.global.exception.AuthException;
import com.team.careerfit.global.security.SessionKeys;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slack 로그인 엔드포인트.
 *
 * <pre>
 *   GET  /api/auth/slack/start?returnTo=/experiences  → 302 Slack 동의 화면
 *   GET  /api/auth/slack/callback?code=..&state=..    → 302 returnTo (세션 발급)
 *   POST /api/auth/logout                             → 204
 * </pre>
 *
 * <p>state 와 PKCE verifier 를 <b>서버 세션이 아니라 짧은 만료의 HttpOnly 쿠키</b>에 담는다.
 * 서버를 재시작해도 진행 중인 로그인이 끊기지 않는다 — 개발 중 재시작이 잦은 3일
 * 프로젝트에서 이게 은근히 크다.
 */
@RestController
@RequestMapping("/api/auth")
public class SlackAuthController {

    private static final String STATE_COOKIE = "cf_oauth_state";
    private static final String VERIFIER_COOKIE = "cf_oauth_verifier";
    private static final String RETURN_TO_COOKIE = "cf_return_to";

    /** 동의 화면을 열어둔 채 자리를 뜰 수 있으니 넉넉히, 그러나 세션보다는 훨씬 짧게. */
    private static final Duration HANDSHAKE_TTL = Duration.ofMinutes(10);

    private final SlackLoginService loginService;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SlackAuthController(SlackLoginService loginService, AuthProperties properties) {
        this.loginService = loginService;
        this.properties = properties;
    }

    /** 프론트의 "Slack 으로 로그인" 버튼이 이 주소로 보낸다. */
    @GetMapping("/slack/start")
    public ResponseEntity<Void> start(
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpServletResponse response) {

        String state = randomToken();
        String verifier = randomToken();

        setHandshakeCookie(response, STATE_COOKIE, state);
        setHandshakeCookie(response, VERIFIER_COOKIE, verifier);
        setHandshakeCookie(response, RETURN_TO_COOKIE, safeReturnPath(returnTo));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(loginService.authorizationUrl(state, challengeOf(verifier))))
                .build();
    }

    /** Slack 이 사용자를 여기로 돌려보낸다. Slack App 설정의 Redirect URL 과 같아야 한다. */
    @GetMapping("/slack/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) {

        String expectedState = readCookie(request, STATE_COOKIE).orElse(null);
        String verifier = readCookie(request, VERIFIER_COOKIE).orElse(null);
        String returnTo = readCookie(request, RETURN_TO_COOKIE).orElse("/");

        // 읽자마자 지운다. 남겨두면 같은 state 로 콜백을 두 번 칠 수 있다.
        clearHandshakeCookies(response);

        requireValidState(state, expectedState);
        if (code == null || code.isBlank() || verifier == null) {
            throw AuthException.loginFailed();
        }

        User user = loginService.completeLogin(code, verifier);

        // 세션 고정 방어 — 로그인 전에 붙어 있던 세션은 버리고 새로 만든다.
        // 이걸 안 하면 공격자가 미리 심어 둔 세션 ID 가 로그인 후에도 그대로 유효하다.
        Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setMaxInactiveInterval((int) properties.sessionTtl().toSeconds());

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(returnTo)).build();
    }

    /** 쿠키만 지우지 않는다. 서버 세션을 폐기해야 이미 세션 ID 를 가진 쪽도 끊긴다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
        return ResponseEntity.noContent().build();
    }

    /**
     * state 대조. 틀리면 400 {@code STATE_MISMATCH}(명세 1절) — 쿠키가 만료된 경우(동의 화면을 10분 넘게
     * 열어 둠)도 같은 응답이다. 사용자가 할 일은 둘 다 "다시 로그인" 하나라 구분하지 않는다.
     *
     * <p>일반 {@code equals} 대신 상수 시간 비교를 쓴다. 응답 시간 차이로 state 를 한
     * 글자씩 맞혀 가는 경로를 남기지 않는다.
     */
    private static void requireValidState(String actual, String expected) {
        if (actual == null
                || expected == null
                || !MessageDigest.isEqual(
                        actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            throw AuthException.stateMismatch();
        }
    }

    /**
     * 열린 리다이렉트 방어.
     *
     * <p>{@code //evil.example} 처럼 슬래시 두 개로 시작하는 값을 브라우저는 다른 호스트로
     * 읽는다. 경로 하나로 시작하는 값만 통과시킨다.
     */
    private static String safeReturnPath(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            return "/";
        }
        return returnTo;
    }

    private void setHandshakeCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(properties.cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) HANDSHAKE_TTL.toSeconds());
        // Strict 로 두면 안 된다. Slack 콜백은 외부 도메인에서 오는 최상위 이동이라
        // Strict 쿠키는 그 요청에 실리지 않고, state 대조가 항상 실패한다.
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearHandshakeCookies(HttpServletResponse response) {
        for (String name : new String[] {STATE_COOKIE, VERIFIER_COOKIE, RETURN_TO_COOKIE}) {
            Cookie cookie = new Cookie(name, "");
            cookie.setHttpOnly(true);
            cookie.setSecure(properties.cookieSecure());
            cookie.setPath("/");
            cookie.setMaxAge(0);
            cookie.setAttribute("SameSite", "Lax");
            response.addCookie(cookie);
        }
    }

    private static Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
