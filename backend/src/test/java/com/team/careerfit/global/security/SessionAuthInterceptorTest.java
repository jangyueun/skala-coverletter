package com.team.careerfit.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.global.exception.AuthException;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 인터셉터 자체의 판정. 경로 규칙(어디에 걸리나)은 {@code AuthWebMvcTest} 가 본다.
 */
class SessionAuthInterceptorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUser currentUser = new CurrentUser(users);
    private final SessionAuthInterceptor interceptor = new SessionAuthInterceptor(currentUser);

    @Test
    void 세션이_없으면_LOGIN_REQUIRED_다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/postings");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    assertThat(((AuthException) e).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(((AuthException) e).code()).isEqualTo("LOGIN_REQUIRED");
                });
    }

    @Test
    void 세션은_있는데_사용자가_지워졌으면_로그인_안_된_것으로_본다() {
        MockHttpServletRequest request = signedInAs(7L);
        when(users.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void 로그인_사용자면_통과하고_컨트롤러는_DB를_다시_읽지_않는다() {
        MockHttpServletRequest request = signedInAs(7L);
        User user = User.firstLogin("T089ENT4A2D", "U7", "지호", null, null);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        // 컨트롤러가 부르는 require() — 인터셉터가 요청 속성에 둔 사용자를 그대로 준다.
        assertThat(currentUser.require(request)).isSameAs(user);
        verify(users, times(1)).findById(7L);
    }

    private static MockHttpServletRequest signedInAs(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/postings");
        request.getSession(true).setAttribute(SessionKeys.USER_ID, userId);
        return request;
    }
}
