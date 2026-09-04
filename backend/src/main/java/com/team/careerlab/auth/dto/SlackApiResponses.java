package com.team.careerlab.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Slack API 응답 매핑.
 *
 * <p><b>Slack 은 실패도 HTTP 200 으로 준다.</b> 본문의 {@code ok} 가 false 이고 {@code error}
 * 에 사유가 담긴다. 상태 코드만 보면 실패를 성공으로 읽는다 — 반드시 ok 를 확인한다.
 *
 * <p>모르는 필드는 무시한다. Slack 이 응답에 필드를 하나 추가하는 날 로그인이 전면
 * 중단되는 것을 막는다.
 */
public final class SlackApiResponses {

    private SlackApiResponses() {}

    /** {@code POST https://slack.com/api/openid.connect.token} 응답. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(
            boolean ok,
            String error,
            @JsonProperty("access_token") String accessToken) {}

    /**
     * {@code GET https://slack.com/api/openid.connect.userInfo} 응답.
     *
     * <p>team_id 는 OIDC 표준 클레임이 아니라 Slack 이 URI 형태로 붙인 커스텀 클레임이다.
     * 필드명이 {@code https://slack.com/team_id} 그대로다 — 오타가 아니다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(
            boolean ok,
            String error,
            @JsonProperty("https://slack.com/team_id") String teamId,
            @JsonProperty("sub") String userId,
            @JsonProperty("name") String displayName,
            @JsonProperty("https://slack.com/user_real_name") String realName,
            String email,
            @JsonProperty("picture") String avatarUrl) {}
}
