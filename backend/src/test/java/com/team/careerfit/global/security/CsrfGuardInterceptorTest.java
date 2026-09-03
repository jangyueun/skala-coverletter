package com.team.careerfit.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.careerfit.global.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfGuardInterceptorTest {

    private final CsrfGuardInterceptor interceptor = new CsrfGuardInterceptor();

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void 교차_사이트에서_온_상태_변경_요청은_403_CSRF_REJECTED_다(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/postings/9/bookmark");
        request.addHeader(CsrfGuardInterceptor.SEC_FETCH_SITE, "cross-site");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    assertThat(((AuthException) e).status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(((AuthException) e).code()).isEqualTo("CSRF_REJECTED");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-origin", "same-site", "none"})
    void 같은_사이트나_주소창_입력은_통과한다(String site) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/questions/31/answer");
        request.addHeader(CsrfGuardInterceptor.SEC_FETCH_SITE, site);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void 헤더가_없는_요청은_통과한다_옛_브라우저와_서버_간_호출() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/experiences");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void GET_은_교차_사이트여도_보지_않는다_Slack_콜백이_그렇다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/slack/callback");
        request.addHeader(CsrfGuardInterceptor.SEC_FETCH_SITE, "cross-site");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
