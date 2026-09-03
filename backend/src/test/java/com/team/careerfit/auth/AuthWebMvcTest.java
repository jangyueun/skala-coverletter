package com.team.careerfit.auth;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerfit.auth.controller.MeController;
import com.team.careerfit.global.config.WebMvcConfig;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.global.security.SessionKeys;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로 규칙이 실제 MVC 파이프라인에 걸리는지 — 어느 경로가 세션을 요구하고, 오류가 어떤 JSON 으로 나가는지.
 * DB 없이 돈다(@WebMvcTest). 인터셉터의 판정 자체는 global/security 의 단위 테스트가 본다.
 */
@WebMvcTest(controllers = MeController.class)
@Import({WebMvcConfig.class, CurrentUser.class, AuthWebMvcTest.ProbeController.class})
@ActiveProfiles("test")
class AuthWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository users;

    @Test
    void 로그인_안_한_상태의_me_는_401_이_아니라_200_null_이다() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void 세션_없이_api_를_부르면_401_LOGIN_REQUIRED_JSON_이다() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void 로그인_사용자는_통과하고_사용자_조회는_요청당_한_번이다() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(User.firstLogin("T089ENT4A2D", "U7", "지호", null, null)));

        mockMvc.perform(get("/api/probe").session(signedInAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("지호"));

        // 인터셉터 + 컨트롤러 require() — DB 는 한 번
        verify(users, times(1)).findById(7L);
    }

    @Test
    void 교차_사이트_POST_는_로그인돼_있어도_403_CSRF_REJECTED_다() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(User.firstLogin("T089ENT4A2D", "U7", "지호", null, null)));

        mockMvc.perform(post("/api/probe").session(signedInAs(7L)).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));

        // 거부는 세션 확인보다 먼저다 — 사용자 조회가 일어나지 않는다.
        verify(users, times(0)).findById(7L);
    }

    @Test
    void 같은_오리진_POST_는_통과한다() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(User.firstLogin("T089ENT4A2D", "U7", "지호", null, null)));

        mockMvc.perform(post("/api/probe").session(signedInAs(7L)).header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isOk());
    }

    @Test
    void internal_경로는_세션을_요구하지_않는다_토큰이_지킨다() throws Exception {
        mockMvc.perform(get("/internal/probe"))
                .andExpect(status().isOk());
    }

    @Test
    void auth_경로는_세션_없이_불러도_인터셉터가_401_로_막지_않는다() throws Exception {
        // SlackAuthController 는 이 슬라이스에 없어 404 가 정답이다. 세션 인터셉터에 걸렸다면 401 이었을 것이다.
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNotFound());
    }

    private static MockHttpSession signedInAs(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, userId);
        return session;
    }

    /** 세션이 필요한 경로와 아닌 경로를 하나씩 — 실제 컨트롤러는 서비스·DB 가 딸려 와서 슬라이스에 못 넣는다. */
    @RestController
    static class ProbeController {

        private final CurrentUser currentUser;

        ProbeController(CurrentUser currentUser) {
            this.currentUser = currentUser;
        }

        @GetMapping("/api/probe")
        Map<String, String> whoAmI(HttpServletRequest request) {
            return Map.of("user", currentUser.require(request).getDisplayName());
        }

        @PostMapping("/api/probe")
        Map<String, String> mutate() {
            return Map.of("ok", "true");
        }

        @GetMapping("/internal/probe")
        Map<String, String> internal() {
            return Map.of("ok", "true");
        }
    }
}
