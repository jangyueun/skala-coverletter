package com.team.careerfit.verification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerfit.global.security.SessionKeys;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * docs/api-spec-v6.md 2·3절(역량 사전·공고 6개 API — 제가 만들지 않은, 팀원 작업)이 컨트롤러
 * 계층까지 실제로 동작하는지 확인한다. V3·V4 마이그레이션이 이미 만들어 둔 카카오페이 공고 시드
 * (요구 역량 3개·문항 2개)를 그대로 쓴다 — 검증용이라 리포지토리에는 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostingApiVerificationTest {

    private static final String KAKAOPAY_SOURCE_URL = "https://jasoseol.com/companies/5463/careers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private static Long userId;
    private static Long postingId;

    @BeforeEach
    void seedOnce() {
        if (userId != null) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        userId = jdbc.sql("""
                insert into users (slack_team_id, slack_user_id, display_name, created_at, last_login_at)
                values (?, ?, ?, ?, ?) returning id
                """)
                .param("T2").param("U2").param("지호").param(now).param(now)
                .query(Long.class).single();

        postingId = jdbc.sql("select id from job_postings where source_url = ?")
                .param(KAKAOPAY_SOURCE_URL)
                .query(Long.class).single();
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, userId);
        return session;
    }

    @Test
    @Order(1)
    void 역량_사전_조회는_시드된_8개를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/competencies").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].category").exists());
    }

    @Test
    @Order(2)
    void 역량_사전은_category_필터가_동작한다() throws Exception {
        mockMvc.perform(get("/api/competencies").session(session()).param("category", "TECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Spring Boot')]").exists());
    }

    @Test
    @Order(3)
    void 공고_목록_조회가_카카오페이_공고를_포함한다() throws Exception {
        mockMvc.perform(get("/api/postings").session(session()).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.items[?(@.id == " + postingId + ")]").exists());
    }

    @Test
    @Order(4)
    void 공고_상세_조회는_요구_역량_3개를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/postings/" + postingId).session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postingId))
                .andExpect(jsonPath("$.company").value("카카오페이"))
                .andExpect(jsonPath("$.requiredCompetencies.length()").value(3))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @Order(5)
    void 존재하지_않는_공고_상세는_404_POSTING_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/api/postings/999999").session(session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @Order(6)
    void 공고_문항_조회는_2개를_돌려주고_답변은_아직_없다() throws Exception {
        mockMvc.perform(get("/api/postings/" + postingId + "/questions").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].answer").doesNotExist());
    }

    @Test
    @Order(7)
    void 매칭_조회는_요구_역량이_있으니_작업을_새로_만들고_PENDING을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/postings/" + postingId + "/match").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.requiredCount").value(3));
    }

    @Test
    @Order(8)
    void 같은_사용자가_다시_조회하면_같은_PENDING_작업을_재사용한다() throws Exception {
        String firstJson = mockMvc.perform(get("/api/postings/" + postingId + "/match").session(session()))
                .andReturn().getResponse().getContentAsString();
        String secondJson = mockMvc.perform(get("/api/postings/" + postingId + "/match").session(session()))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(firstJson).isEqualTo(secondJson);
    }

    @Test
    @Order(9)
    void 북마크를_켜고_끄면_그대로_반영된다() throws Exception {
        mockMvc.perform(put("/api/postings/" + postingId + "/bookmark").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookmarked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postingId").value(postingId))
                .andExpect(jsonPath("$.bookmarked").value(true));

        mockMvc.perform(get("/api/postings/" + postingId).session(session()))
                .andExpect(jsonPath("$.bookmarked").value(true));

        mockMvc.perform(put("/api/postings/" + postingId + "/bookmark").session(session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookmarked\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(false));
    }

    @Test
    @Order(10)
    void 로그인_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(get("/api/postings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/competencies")).andExpect(status().isUnauthorized());
    }
}
