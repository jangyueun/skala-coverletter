package com.team.careerlab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.auth.dto.SlackProfile;
import com.team.careerlab.auth.service.SlackLoginService;
import com.team.careerlab.auth.service.SlackOAuthClient;
import com.team.careerlab.global.config.AuthProperties;
import com.team.careerlab.global.exception.AuthException;
import com.team.careerlab.user.entity.User;
import com.team.careerlab.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 워크스페이스 제한이 실제로 걸리는지 본다.
 *
 * <p>이 테스트가 깨지면 아무나 로그인된다. 다른 건 몰라도 이건 있어야 한다.
 */
class SlackLoginServiceTest {

    private static final String OUR_TEAM = "T089ENT4A2D";

    private final SlackOAuthClient slack = mock(SlackOAuthClient.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SlackLoginService service = new SlackLoginService(
            slack,
            users,
            new AuthProperties("id", "secret", OUR_TEAM, "https://x/callback", true, Duration.ofDays(14)));

    private void slackReturns(SlackProfile profile) {
        when(slack.exchange(anyString(), anyString())).thenReturn(profile);
    }

    @Test
    void 다른_워크스페이스_계정은_거부된다() {
        slackReturns(new SlackProfile("T_OTHER_WORKSPACE", "U123", "남", null, null, null));

        assertThatThrownBy(() -> service.completeLogin("code", "verifier"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 거부된 계정이 DB 에 남으면 안 된다.
        verify(users, never()).save(any());
    }

    @Test
    void team_id_가_비어_있으면_거부된다() {
        // Slack 응답 형식이 바뀌어 team_id 가 안 오는 날, 검사가 통째로 무력해지면 안 된다.
        slackReturns(new SlackProfile(null, "U123", "이름", null, null, null));

        assertThatThrownBy(() -> service.completeLogin("code", "verifier"))
                .isInstanceOf(AuthException.class);
        verify(users, never()).save(any());
    }

    @Test
    void 우리_워크스페이스_최초_로그인이면_사용자를_만든다() {
        slackReturns(new SlackProfile(OUR_TEAM, "U123", "지호", "김지호", "a@b.c", "http://img"));
        when(users.findBySlackTeamIdAndSlackUserId(OUR_TEAM, "U123")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.completeLogin("code", "verifier");

        assertThat(saved.getSlackTeamId()).isEqualTo(OUR_TEAM);
        assertThat(saved.getSlackUserId()).isEqualTo("U123");
        assertThat(saved.getDisplayName()).isEqualTo("지호");
    }

    @Test
    void 이미_있는_사용자면_새로_만들지_않고_갱신한다() {
        slackReturns(new SlackProfile(OUR_TEAM, "U123", "새이름", null, "new@b.c", null));
        User existing = User.firstLogin(OUR_TEAM, "U123", "옛이름", "old@b.c", null);
        when(users.findBySlackTeamIdAndSlackUserId(OUR_TEAM, "U123")).thenReturn(Optional.of(existing));

        User result = service.completeLogin("code", "verifier");

        assertThat(result.getDisplayName()).isEqualTo("새이름");
        assertThat(result.getEmail()).isEqualTo("new@b.c");
        verify(users, never()).save(any());
    }

    @Test
    void 이름이_없는_계정도_로그인된다() {
        // 워크스페이스 설정에 따라 표시 이름이 안 오는 계정이 있다. 로그인이 막히면 안 된다.
        slackReturns(new SlackProfile(OUR_TEAM, "U999", null, null, null, null));
        when(users.findBySlackTeamIdAndSlackUserId(OUR_TEAM, "U999")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.completeLogin("code", "verifier").getDisplayName()).isEqualTo("사용자 U999");
    }
}
