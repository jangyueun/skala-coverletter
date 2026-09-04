package com.team.careerlab.user.dto;

import com.team.careerlab.user.entity.User;

/**
 * 클라이언트에 내보내는 사용자 정보.
 *
 * <p>slackUserId 와 slackTeamId 는 넣지 않는다. 화면에 필요 없고, 내부 식별자를
 * 노출하면 다른 사용자를 지목하는 요청을 만들기 쉬워진다.
 */
public record UserResponse(Long id, String displayName, String email, String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getAvatarUrl());
    }
}
