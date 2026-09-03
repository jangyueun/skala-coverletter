package com.team.careerfit.verification;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerfit.global.security.SessionKeys;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * docs/api-spec-v6.md 4·5절(경험·자기소개서 7개 API)이 실제로 그 요청·응답 모양대로 동작하는지
 * 컨트롤러 레이어(JSON 직렬화 포함)까지 통째로 확인한다. 검증용이라 리포지토리에는 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSpecVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private static Long userId;
    private static Long competencyId;
    private static Long questionId;
    private static Long experienceId;

    @BeforeEach
    void seedOnce() {
        if (userId != null) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp deadline = Timestamp.from(Instant.now().plusSeconds(86400));

        userId = jdbc.sql("""
                insert into users (slack_team_id, slack_user_id, display_name, created_at, last_login_at)
                values (?, ?, ?, ?, ?) returning id
                """)
                .param("T1").param("U1").param("지호").param(now).param(now)
                .query(Long.class).single();

        competencyId = jdbc.sql("""
                insert into competencies (name, category, created_at, updated_at)
                values (?, ?, ?, ?) returning id
                """)
                .param("API 설계·연동").param("ROLE").param(now).param(now)
                .query(Long.class).single();

        Long companyId = jdbc.sql("""
                insert into companies (name, normalized_name, created_at, updated_at)
                values (?, ?, ?, ?) returning id
                """)
                .param("세움테크").param("세움테크").param(now).param(now)
                .query(Long.class).single();

        Long postingId = jdbc.sql("""
                insert into job_postings (company_id, position, content, deadline, status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', ?, ?) returning id
                """)
                .param(companyId).param("백엔드 엔지니어").param("공고 본문")
                .param(deadline).param(now).param(now)
                .query(Long.class).single();

        questionId = jdbc.sql("""
                insert into job_posting_questions (job_posting_id, sequence, prompt_text, created_at, updated_at)
                values (?, 1, ?, ?, ?) returning id
                """)
                .param(postingId).param("자신의 강점을 서술하시오.").param(now).param(now)
                .query(Long.class).single();
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, userId);
        return session;
    }

    @Test
    @Order(1)
    void 로그인_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(get("/api/experiences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void 경험_목록은_처음에_빈_배열이다() throws Exception {
        mockMvc.perform(get("/api/experiences").session(session()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @Order(3)
    void 역량이_없으면_경험_등록이_400_VALIDATION_FAILED다() throws Exception {
        String body = """
                {"title":"","category":"TEAM_PROJECT","result":"","competencies":[]}
                """;
        mockMvc.perform(post("/api/experiences").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(4)
    void 경험_등록_응답이_명세와_일치하고_활성_공고에_MATCH_작업이_생긴다() throws Exception {
        String body = """
                {"title":"MSA 주문·결제 서비스 구축","category":"TEAM_PROJECT","startDate":"2026-08-01","endDate":null,
                 "situation":"s","task":"t","action":"a","result":"r",
                 "competencies":[{"competencyId":%d,"strength":0.8}]}
                """.formatted(competencyId);

        String json = mockMvc.perform(post("/api/experiences").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.experience.id").exists())
                .andExpect(jsonPath("$.experience.title").value("MSA 주문·결제 서비스 구축"))
                .andExpect(jsonPath("$.experience.category").value("TEAM_PROJECT"))
                .andExpect(jsonPath("$.experience.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.experience.endDate").doesNotExist())
                .andExpect(jsonPath("$.experience.aiTaskId").doesNotExist())
                .andExpect(jsonPath("$.experience.competencies[0].competencyId").value(competencyId))
                .andExpect(jsonPath("$.experience.competencies[0].name").value("API 설계·연동"))
                .andExpect(jsonPath("$.experience.competencies[0].strength").value(0.8))
                .andExpect(jsonPath("$.experience.usedInQuestions").value(0))
                // 활성 공고 개수는 다른 마이그레이션의 시드 데이터에 따라 달라진다 — 1개 이상만 확인한다.
                .andExpect(jsonPath("$.reassess.postingCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.reassess.taskIds[0]").exists())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(json, Map.class);
        Map<?, ?> experience = (Map<?, ?>) root.get("experience");
        experienceId = ((Number) experience.get("id")).longValue();
    }

    @Test
    @Order(5)
    void 경험_목록에_방금_등록한_경험이_보인다() throws Exception {
        mockMvc.perform(get("/api/experiences").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(experienceId))
                .andExpect(jsonPath("$[0].usedInQuestions").value(0));
    }

    @Test
    @Order(6)
    void 경험_수정하면_바뀐_값과_reassess가_돌아온다() throws Exception {
        String body = """
                {"title":"MSA 주문·결제 서비스 구축 v2","category":"TEAM_PROJECT","startDate":"2026-08-01","endDate":null,
                 "situation":"s2","task":"t2","action":"a2","result":"r2",
                 "competencies":[{"competencyId":%d,"strength":0.9}]}
                """.formatted(competencyId);

        mockMvc.perform(put("/api/experiences/" + experienceId).session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experience.title").value("MSA 주문·결제 서비스 구축 v2"))
                .andExpect(jsonPath("$.experience.competencies[0].strength").value(0.9))
                .andExpect(jsonPath("$.reassess.postingCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(7)
    void 존재하지_않는_경험_수정은_404_EXPERIENCE_NOT_FOUND다() throws Exception {
        String body = """
                {"title":"x","category":"TEAM_PROJECT","result":"x",
                 "competencies":[{"competencyId":%d,"strength":0.5}]}
                """.formatted(competencyId);

        mockMvc.perform(put("/api/experiences/999999").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
    }

    @Test
    @Order(8)
    void 인테이크는_링크만으로도_202와_taskId를_준다() throws Exception {
        mockMvc.perform(multipart("/api/experience-intakes").session(session())
                        .param("links", "https://github.com/example\nhttps://blog.example.com/post"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists());
    }

    @Test
    @Order(9)
    void 인테이크_허용되지_않는_확장자는_업로드_없이_400이다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "malware.exe", "application/octet-stream",
                "x".getBytes());
        mockMvc.perform(multipart("/api/experience-intakes").session(session()).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(10)
    void 링크도_파일도_없으면_400이다() throws Exception {
        mockMvc.perform(multipart("/api/experience-intakes").session(session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(11)
    void 답변_없는_문항_조회는_빈_값이다() throws Exception {
        mockMvc.perform(get("/api/questions/" + questionId + "/answer").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.usedExperienceIds").isEmpty())
                .andExpect(jsonPath("$.aiTaskId").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    @Order(12)
    void 존재하지_않는_문항_조회는_404_QUESTION_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/api/questions/999999/answer").session(session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    @Order(13)
    void 답변을_저장하면_명세대로_돌아온다() throws Exception {
        String body = """
                {"content":"제 강점은 문제 해결 능력입니다.","usedExperienceIds":[%d],"draftTaskId":null}
                """.formatted(experienceId);

        mockMvc.perform(put("/api/questions/" + questionId + "/answer").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.content").value("제 강점은 문제 해결 능력입니다."))
                .andExpect(jsonPath("$.charCount").value(18))
                .andExpect(jsonPath("$.usedExperienceIds[0]").value(experienceId))
                .andExpect(jsonPath("$.aiTaskId").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @Order(14)
    void 남의_경험을_근거로_쓰면_403_FORBIDDEN이다() throws Exception {
        String body = """
                {"content":"내용","usedExperienceIds":[999999],"draftTaskId":null}
                """;
        mockMvc.perform(put("/api/questions/" + questionId + "/answer").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @Order(15)
    void 저장한_답변을_다시_조회하면_그대로_나온다() throws Exception {
        mockMvc.perform(get("/api/questions/" + questionId + "/answer").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("제 강점은 문제 해결 능력입니다."));
    }

    @Test
    @Order(16)
    void 초안_요청은_202와_taskId를_준다() throws Exception {
        String body = "{\"experienceIds\":[%d]}".formatted(experienceId);

        mockMvc.perform(post("/api/questions/" + questionId + "/drafts").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists());
    }

    @Test
    @Order(17)
    void 초안_근거_경험이_비어있으면_400이다() throws Exception {
        mockMvc.perform(post("/api/questions/" + questionId + "/drafts").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"experienceIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
