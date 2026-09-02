package com.team.careerfit.auth.service;

import com.team.careerfit.auth.dto.SlackProfile;
import com.team.careerfit.global.config.AuthProperties;
import com.team.careerfit.global.exception.AuthException;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인가 코드를 받은 다음부터 — 워크스페이스 확인 → 사용자 생성·갱신 — 을 맡는다.
 *
 * <p>state 검증과 쿠키·세션 취급은 컨트롤러가 한다. 여기서는 하지 않는다.
 */
@Service
public class SlackLoginService {

    private static final Logger log = LoggerFactory.getLogger(SlackLoginService.class);

    private final SlackOAuthClient slackOAuth;
    private final UserRepository users;
    private final AuthProperties properties;

    public SlackLoginService(SlackOAuthClient slackOAuth, UserRepository users, AuthProperties properties) {
        this.slackOAuth = slackOAuth;
        this.users = users;
        this.properties = properties;
    }

    public String authorizationUrl(String state, String codeChallenge) {
        return slackOAuth.authorizationUrl(state, codeChallenge);
    }

    /**
     * 인가 코드로 로그인을 마친다.
     *
     * @return 로그인된 사용자
     * @throws AuthException 허용되지 않은 워크스페이스면 403, 그 외 실패는 401
     */
    @Transactional
    public User completeLogin(String code, String codeVerifier) {
        SlackProfile profile = slackOAuth.exchange(code, codeVerifier);
        requireAllowedWorkspace(profile);

        String displayName = resolveDisplayName(profile);

        return users.findBySlackTeamIdAndSlackUserId(profile.teamId(), profile.userId())
                .map(existing -> {
                    existing.syncFromSlack(displayName, profile.email(), profile.avatarUrl());
                    return existing;
                })
                .orElseGet(() -> users.save(User.firstLogin(
                        profile.teamId(), profile.userId(), displayName, profile.email(), profile.avatarUrl())));
    }

    /**
     * 워크스페이스 제한. <b>이 서비스를 거치지 않고 로그인되는 경로는 없다.</b>
     *
     * <p>team_id 는 Slack 의 userInfo 응답에서 온다. 클라이언트가 보낸 값이 아니라
     * 우리 서버가 액세스 토큰으로 직접 조회한 값이므로 위조할 수 없다.
     *
     * <p>비어 있는 값도 거부한다. null 을 통과시키면 검사가 통째로 무력해진다 —
     * Slack 응답 형식이 바뀌어 team_id 가 안 오는 날 전원 로그인이 뚫린다.
     */
    private void requireAllowedWorkspace(SlackProfile profile) {
        if (profile.teamId() == null
                || profile.teamId().isBlank()
                || !profile.teamId().equals(properties.allowedTeamId())) {
            // 어느 워크스페이스였는지는 로그에만. 응답에 담으면 공격자에게 힌트가 된다.
            log.info("허용되지 않은 워크스페이스의 로그인 시도: teamId={}", profile.teamId());
            throw AuthException.workspaceNotAllowed();
        }
        if (profile.userId() == null || profile.userId().isBlank()) {
            log.warn("Slack 사용자 ID 가 비어 있다. 식별할 수 없어 거부한다.");
            throw AuthException.loginFailed();
        }
    }

    /** Slack 이 이름을 안 주는 계정이 있다. 그때는 사용자 ID 로 대체해 화면이 비지 않게 한다. */
    private static String resolveDisplayName(SlackProfile profile) {
        if (profile.displayName() != null && !profile.displayName().isBlank()) {
            return profile.displayName();
        }
        if (profile.realName() != null && !profile.realName().isBlank()) {
            return profile.realName();
        }
        return "사용자 " + profile.userId();
    }
}
