package com.team.careerlab.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 서비스 사용자. Slack 계정 하나에 하나씩 생긴다.
 *
 * <p><b>식별 키는 (slackTeamId, slackUserId) 다.</b> 이메일이 아니다. 이유가 셋 있다 —
 * 이메일은 사용자가 바꿀 수 있고, 워크스페이스 설정에 따라 아예 안 오는 계정이 있고,
 * 워크스페이스가 달라도 같은 이메일이 존재할 수 있다. 이메일을 키로 잡으면 나중에
 * 워크스페이스를 하나 더 열 때 계정이 충돌한다.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_slack_identity",
                columnNames = {"slack_team_id", "slack_user_id"}))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slack_team_id", nullable = false, length = 32)
    private String slackTeamId;

    @Column(name = "slack_user_id", nullable = false, length = 32)
    private String slackUserId;

    /** 화면에 쓰는 이름. 로그인할 때마다 Slack 값으로 덮는다. */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** 워크스페이스 설정에 따라 안 올 수 있다. 없으면 null 이다 — 로그인은 계속된다. */
    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected User() {
        // JPA 용
    }

    private User(String slackTeamId, String slackUserId, String displayName, String email, String avatarUrl) {
        this.slackTeamId = slackTeamId;
        this.slackUserId = slackUserId;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public static User firstLogin(String slackTeamId, String slackUserId, String displayName, String email, String avatarUrl) {
        return new User(slackTeamId, slackUserId, displayName, email, avatarUrl);
    }

    /**
     * 재로그인 시 Slack 쪽 최신 값으로 맞춘다.
     *
     * <p>표시 이름은 Slack 을 따라간다. 워크스페이스에서 이름을 바꾸면 여기도 바뀐다 —
     * 그래서 <b>표시 이름이나 이메일로 권한을 판단하면 안 된다.</b> 이름만 바꿔서
     * 남의 권한을 가져가는 경로가 된다. 권한은 slackUserId 로만 본다.
     */
    public void syncFromSlack(String displayName, String email, String avatarUrl) {
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.lastLoginAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSlackTeamId() {
        return slackTeamId;
    }

    public String getSlackUserId() {
        return slackUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
