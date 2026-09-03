package com.team.careerfit.auth.dto;

/**
 * Slack 이 돌려준 사용자 정보 중 우리가 쓰는 것만.
 *
 * <p>어느 필드든 비어 있을 수 있다는 전제로 다룬다 — 워크스페이스 설정에 따라 표시
 * 이름이나 이메일이 오지 않는 계정이 있다. teamId 와 userId 만은 반드시 있어야 한다.
 *
 * @param teamId 워크스페이스 ID. 허용 목록과 대조하는 값
 * @param userId Slack 사용자 ID. <b>내부 식별의 기준</b>이다. 이메일을 키로 쓰면 안 된다 —
 *     이메일은 바뀔 수 있고 워크스페이스 설정에 따라 아예 안 올 수도 있다
 */
public record SlackProfile(
        String teamId,
        String userId,
        String displayName,
        String realName,
        String email,
        String avatarUrl) {}
