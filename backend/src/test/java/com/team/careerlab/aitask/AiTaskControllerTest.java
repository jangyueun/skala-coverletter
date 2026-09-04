package com.team.careerlab.aitask;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerlab.aitask.controller.AiTaskController;
import com.team.careerlab.aitask.dto.AiTaskListResponse;
import com.team.careerlab.aitask.dto.AiTaskResponse;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.exception.AiTaskException;
import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.global.config.WebMvcConfig;
import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.global.security.SessionKeys;
import com.team.careerlab.user.entity.User;
import com.team.careerlab.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 폴링 API 의 HTTP 모양(명세 §6). 서비스는 목이고, DB 없이 돈다. */
@WebMvcTest(controllers = AiTaskController.class)
@Import({WebMvcConfig.class, CurrentUser.class})
@ActiveProfiles("test")
class AiTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiTaskService aiTasks;

    @MockitoBean
    private UserRepository users;

    @Test
    void 완료된_작업은_result_를_준다() throws Exception {
        signedInAs(7L);
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        when(aiTasks.find(7L, 821L)).thenReturn(new AiTaskResponse(821L, AiTaskType.DRAFT, AiTaskStatus.COMPLETED,
                now, now, 1, "claude-opus-5", "draft/v1",
                new JsonMapper().readTree("{\"draft\":\"초안\",\"charCount\":2}"), null));

        mockMvc.perform(get("/api/ai-tasks/821").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(821))
                .andExpect(jsonPath("$.type").value("DRAFT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.promptVersion").value("draft/v1"))
                .andExpect(jsonPath("$.result.draft").value("초안"))
                .andExpect(jsonPath("$.result.charCount").value(2))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.completedAt").value("2026-09-04T10:00:00Z"));
    }

    @Test
    void 실패한_작업은_error_를_준다() throws Exception {
        signedInAs(7L);
        when(aiTasks.find(7L, 790L)).thenReturn(new AiTaskResponse(790L, AiTaskType.EXPERIENCE_INTAKE,
                AiTaskStatus.FAILED, Instant.now(), Instant.now(), 3, null, null, null,
                new AiTaskResponse.Error("AI_PROVIDER_ERROR", "AI 제공자 호출에 반복 실패했습니다.")));

        mockMvc.perform(get("/api/ai-tasks/790").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.error.code").value("AI_PROVIDER_ERROR"));
    }

    @Test
    void 남의_작업은_403_없는_작업은_404() throws Exception {
        signedInAs(7L);
        when(aiTasks.find(7L, 1L)).thenThrow(AiTaskException.forbidden());
        when(aiTasks.find(7L, 2L)).thenThrow(AiTaskException.taskNotFound());

        mockMvc.perform(get("/api/ai-tasks/1").session(session(7L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/ai-tasks/2").session(session(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void 세션이_없으면_401() throws Exception {
        mockMvc.perform(get("/api/ai-tasks/821"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 목록은_필터를_넘기고_잘못된_enum_은_400() throws Exception {
        signedInAs(7L);
        when(aiTasks.list(eq(7L), eq(AiTaskType.MATCH), eq(Set.of(AiTaskStatus.RUNNING)), isNull()))
                .thenReturn(new AiTaskListResponse(new AiTaskListResponse.Counts(0, 1, 0, 0), List.of(
                        new AiTaskListResponse.Item(813L, AiTaskType.MATCH, AiTaskStatus.RUNNING, 9L, null,
                                Instant.now()))));

        mockMvc.perform(get("/api/ai-tasks").session(session(7L)).param("type", "MATCH").param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.running").value(1))
                .andExpect(jsonPath("$.items[0].taskId").value(813))
                .andExpect(jsonPath("$.items[0].postingId").value(9));

        mockMvc.perform(get("/api/ai-tasks").session(session(7L)).param("type", "WHAT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** 컨트롤러가 user.getId() 를 서비스에 넘기므로 픽스처에도 id 가 있어야 목이 맞는다. */
    private void signedInAs(Long userId) {
        User user = User.firstLogin("T089ENT4A2D", "U" + userId, "지호", null, null);
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    private static MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, userId);
        return session;
    }
}
