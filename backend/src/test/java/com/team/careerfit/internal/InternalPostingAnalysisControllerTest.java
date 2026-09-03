package com.team.careerfit.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class InternalPostingAnalysisControllerTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("careerfit.internal.token", () -> INTERNAL_TOKEN);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void 새_공고_분석_작업을_등록한다() throws Exception {
        Long postingId = insertPosting("Java와 Spring Boot 기반 REST API 개발");
        insertCompetency();

        mockMvc.perform(post("/internal/postings/{postingId}/analysis", postingId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isNumber());

        String taskType = jdbcClient.sql("""
                        select task_type from ai_tasks
                        where job_posting_id = :postingId
                        """)
                .param("postingId", postingId)
                .query(String.class)
                .single();
        String payload = jdbcClient.sql("""
                        select request_payload::text from ai_tasks
                        where job_posting_id = :postingId
                        """)
                .param("postingId", postingId)
                .query(String.class)
                .single();

        org.assertj.core.api.Assertions.assertThat(taskType).isEqualTo("POSTING_ANALYSIS");
        org.assertj.core.api.Assertions.assertThat(payload)
                .contains("Java와 Spring Boot 기반 REST API 개발")
                .contains("API 설계");
    }

    @Test
    void 같은_입력은_기존_작업을_반환한다() throws Exception {
        Long postingId = insertPosting("같은 입력의 공고 본문");

        mockMvc.perform(post("/internal/postings/{postingId}/analysis", postingId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isAccepted());
        Long taskId = jdbcClient.sql("select id from ai_tasks where job_posting_id = :postingId")
                .param("postingId", postingId)
                .query(Long.class)
                .single();

        mockMvc.perform(post("/internal/postings/{postingId}/analysis", postingId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        Integer count = jdbcClient.sql("select count(*) from ai_tasks where job_posting_id = :postingId")
                .param("postingId", postingId)
                .query(Integer.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void 다른_입력의_작업이_진행_중이면_409를_응답한다() throws Exception {
        Long postingId = insertPosting("변경 전 공고 본문");
        mockMvc.perform(post("/internal/postings/{postingId}/analysis", postingId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isAccepted());
        jdbcClient.sql("update job_postings set content = :content where id = :postingId")
                .param("content", "변경 후 공고 본문")
                .param("postingId", postingId)
                .update();

        mockMvc.perform(post("/internal/postings/{postingId}/analysis", postingId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_RUNNING"));
    }

    @Test
    void 토큰이_없거나_다르면_401을_응답한다() throws Exception {
        mockMvc.perform(post("/internal/postings/{postingId}/analysis", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_TOKEN_INVALID"));

        mockMvc.perform(post("/internal/postings/{postingId}/analysis", 1)
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_TOKEN_INVALID"));
    }

    @Test
    void 없는_공고는_404를_응답한다() throws Exception {
        mockMvc.perform(post("/internal/postings/{postingId}/analysis", Long.MAX_VALUE)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    private Long insertPosting(String content) {
        String suffix = Long.toString(System.nanoTime());
        Long companyId = jdbcClient.sql("""
                        insert into companies (name, normalized_name, created_at, updated_at)
                        values (:name, :normalizedName, now(), now())
                        returning id
                        """)
                .param("name", "내부 API 테스트 기업 " + suffix)
                .param("normalizedName", "internal-api-test-" + suffix)
                .query(Long.class)
                .single();
        return jdbcClient.sql("""
                        insert into job_postings
                            (company_id, position, content, source_url, deadline, status, created_at, updated_at)
                        values
                            (:companyId, '백엔드 개발자', :content, :sourceUrl,
                             now() + interval '30 days', 'ACTIVE', now(), now())
                        returning id
                        """)
                .param("companyId", companyId)
                .param("content", content)
                .param("sourceUrl", "https://example.com/postings/" + suffix)
                .query(Long.class)
                .single();
    }

    private void insertCompetency() {
        String suffix = Long.toString(System.nanoTime());
        jdbcClient.sql("""
                        insert into competencies (name, category, created_at, updated_at)
                        values (:name, 'ROLE', now(), now())
                        """)
                .param("name", "API 설계 " + suffix)
                .update();
    }
}
