package com.team.careerfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerfit.global.security.SessionKeys;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
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
class CareerfitApplicationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Test
    void contextLoads() {
        Integer appliedMigrations = jdbcClient.sql("""
                        select count(*)
                        from "flyway_schema_history"
                        where "success" = true and "version" in ('1', '2', '3')
                        """)
                .query(Integer.class)
                .single();

        assertThat(appliedMigrations).isEqualTo(3);
    }

    @Test
    void it_공고_시드_10건이_적용된다() {
        Integer postingCount = jdbcClient.sql("""
                        select count(*)
                        from job_postings
                        where source_url like 'https://jasoseol.com/%'
                        """)
                .query(Integer.class)
                .single();

        assertThat(postingCount).isEqualTo(10);
    }

    @Test
    void 로그인한_사용자는_공고_목록을_조회한다() throws Exception {
        mockMvc.perform(get("/api/postings")
                        .session(loginSession("U_POSTING_LIST"))
                        .queryParam("sort", "deadline")
                        .queryParam("page", "0")
                        .queryParam("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].company").value("에코마케팅"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[0].match").isEmpty())
                .andExpect(jsonPath("$.items[0].essay.state").value("NO_QUESTIONS"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalCount").value(10));
    }

    @Test
    void 회사명으로_공고를_검색한다() throws Exception {
        mockMvc.perform(get("/api/postings")
                        .session(loginSession("U_POSTING_SEARCH"))
                        .queryParam("q", "카카오페이"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].position").value("서버 개발자 - 데이터 플랫폼 (신입)"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 역량과_북마크를_필터링하고_매칭과_자소서_진행률을_반환한다() throws Exception {
        MockHttpSession session = loginSession("U_POSTING_FILTER");
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        Long postingId = jdbcClient.sql("""
                        select id
                        from job_postings
                        where position = '서버 개발자 - 데이터 플랫폼 (신입)'
                        """)
                .query(Long.class)
                .single();
        Long competencyId = jdbcClient.sql("""
                        insert into competencies (name, category, created_at, updated_at)
                        values ('Spring Boot 테스트 역량', 'TECH', now(), now())
                        returning id
                        """)
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                        insert into posting_competencies
                            (job_posting_id, competency_id, weight, evidence_line, created_at, updated_at)
                        values (:postingId, :competencyId, 0.80, 'Spring Boot 기반 서버 개발', now(), now())
                        """)
                .param("postingId", postingId)
                .param("competencyId", competencyId)
                .update();
        jdbcClient.sql("""
                        insert into bookmarks (user_id, job_posting_id, created_at)
                        values (:userId, :postingId, now())
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .update();
        jdbcClient.sql("""
                        insert into job_matches
                            (user_id, job_posting_id, match_score, verdict, covered_count, coverage,
                             input_hash, created_at, updated_at)
                        values (:userId, :postingId, 0.755, 'CONDITIONAL', 1,
                                cast(:coverage as jsonb), 'test-input-hash', now(), now())
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .param("coverage", "[{\"competencyId\":" + competencyId + ",\"isGap\":false}]")
                .update();
        Long firstQuestionId = jdbcClient.sql("""
                        insert into job_posting_questions
                            (job_posting_id, sequence, prompt_text, length_limit, created_at, updated_at)
                        values (:postingId, 1, '지원 동기를 작성해 주세요.', 1000, now(), now())
                        returning id
                        """)
                .param("postingId", postingId)
                .query(Long.class)
                .single();
        jdbcClient.sql("""
                        insert into job_posting_questions
                            (job_posting_id, sequence, prompt_text, length_limit, created_at, updated_at)
                        values (:postingId, 2, '직무 역량을 작성해 주세요.', 1000, now(), now())
                        """)
                .param("postingId", postingId)
                .update();
        jdbcClient.sql("""
                        insert into cover_letter_answers
                            (user_id, question_id, content, char_count, used_experience_ids,
                             created_at, updated_at)
                        values (:userId, :questionId, '작성 중인 답변', 8, '{}', now(), now())
                        """)
                .param("userId", userId)
                .param("questionId", firstQuestionId)
                .update();

        mockMvc.perform(get("/api/postings")
                        .session(session)
                        .queryParam("competencyId", competencyId.toString(), "999999")
                        .queryParam("bookmarked", "true")
                        .queryParam("sort", "match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].company").value("카카오페이"))
                .andExpect(jsonPath("$.items[0].bookmarked").value(true))
                .andExpect(jsonPath("$.items[0].match.score").value(76))
                .andExpect(jsonPath("$.items[0].match.verdict").value("CONDITIONAL"))
                .andExpect(jsonPath("$.items[0].match.coveredCompetencyNames[0]")
                        .value("Spring Boot 테스트 역량"))
                .andExpect(jsonPath("$.items[0].match.requiredCount").value(1))
                .andExpect(jsonPath("$.items[0].essay.state").value("WRITING"))
                .andExpect(jsonPath("$.items[0].essay.answered").value(1))
                .andExpect(jsonPath("$.items[0].essay.total").value(2))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 공고_상세와_요구_역량과_유사_공고를_조회한다() throws Exception {
        MockHttpSession session = loginSession("U_POSTING_DETAIL");
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        Long targetId = postingId("서버 개발자 - 데이터 플랫폼 (신입)");
        Long similarId = postingId("Forward Deployed Engineer(FDE) (신입)");
        Long competencyId = insertCompetency("상세 API 테스트 역량");
        insertPostingCompetency(targetId, competencyId, "0.90", "상세 조회 요구 역량 근거");
        insertPostingCompetency(similarId, competencyId, "0.70", "유사 공고 요구 역량 근거");
        insertMatch(userId, similarId, "0.630", "CONDITIONAL", competencyId);

        mockMvc.perform(get("/api/postings/{postingId}", targetId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId))
                .andExpect(jsonPath("$.company").value("카카오페이"))
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.requiredCompetencies[?(@.competencyId == " + competencyId + ")].name")
                        .value("상세 API 테스트 역량"))
                .andExpect(jsonPath("$.related.sameCompany.length()").value(0))
                .andExpect(jsonPath("$.related.similar[0].id").value(similarId))
                .andExpect(jsonPath("$.related.similar[0].sharedCompetencyCount").value(1))
                .andExpect(jsonPath("$.related.similar[0].score").value(63));
    }

    @Test
    void 존재하지_않는_공고_상세는_404를_응답한다() throws Exception {
        mockMvc.perform(get("/api/postings/{postingId}", 999999)
                        .session(loginSession("U_POSTING_DETAIL_NOT_FOUND")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    void 공고_문항과_로그인_사용자의_답변을_조회한다() throws Exception {
        MockHttpSession session = loginSession("U_POSTING_QUESTIONS");
        Long userId = (Long) session.getAttribute(SessionKeys.USER_ID);
        Long targetId = postingId("AI/AX 개발 (신입)");
        Long answeredQuestionId = insertQuestion(targetId, 1, "지원 동기를 작성해 주세요.", 700);
        insertQuestion(targetId, 2, "프로젝트 경험을 작성해 주세요.", null);
        jdbcClient.sql("""
                        insert into cover_letter_answers
                            (user_id, question_id, content, char_count, used_experience_ids,
                             created_at, updated_at)
                        values (:userId, :questionId, '테스트 답변', 6, '{11,12}', now(), now())
                        """)
                .param("userId", userId)
                .param("questionId", answeredQuestionId)
                .update();

        mockMvc.perform(get("/api/postings/{postingId}/questions", targetId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sequence").value(1))
                .andExpect(jsonPath("$[0].lengthLimit").value(700))
                .andExpect(jsonPath("$[0].answer.content").value("테스트 답변"))
                .andExpect(jsonPath("$[0].answer.usedExperienceIds[0]").value(11))
                .andExpect(jsonPath("$[0].answer.usedExperienceIds[1]").value(12))
                .andExpect(jsonPath("$[0].answer.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].answer").isEmpty());
    }

    @Test
    void 문항_조회에서도_존재하지_않는_공고는_404를_응답한다() throws Exception {
        mockMvc.perform(get("/api/postings/{postingId}/questions", 999999)
                        .session(loginSession("U_POSTING_QUESTIONS_NOT_FOUND")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    void 페이지_크기가_허용_범위를_넘으면_400을_응답한다() throws Exception {
        mockMvc.perform(get("/api/postings")
                        .session(loginSession("U_POSTING_INVALID_QUERY"))
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("공고 조회 조건이 올바르지 않습니다."));
    }

    @Test
    void 로그인하지_않으면_공고_목록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/postings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    private MockHttpSession loginSession(String slackUserId) {
        User user = users.save(User.firstLogin(
                "T_TEST",
                slackUserId,
                "공고 조회 사용자",
                slackUserId.toLowerCase() + "@example.com",
                null));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        return session;
    }

    private Long postingId(String position) {
        return jdbcClient.sql("select id from job_postings where position = :position")
                .param("position", position)
                .query(Long.class)
                .single();
    }

    private Long insertCompetency(String name) {
        return jdbcClient.sql("""
                        insert into competencies (name, category, created_at, updated_at)
                        values (:name, 'TECH', now(), now())
                        returning id
                        """)
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private void insertPostingCompetency(Long postingId, Long competencyId, String weight, String evidenceLine) {
        jdbcClient.sql("""
                        insert into posting_competencies
                            (job_posting_id, competency_id, weight, evidence_line, created_at, updated_at)
                        values (:postingId, :competencyId, cast(:weight as decimal), :evidenceLine, now(), now())
                        """)
                .param("postingId", postingId)
                .param("competencyId", competencyId)
                .param("weight", weight)
                .param("evidenceLine", evidenceLine)
                .update();
    }

    private void insertMatch(
            Long userId,
            Long postingId,
            String score,
            String verdict,
            Long competencyId) {
        jdbcClient.sql("""
                        insert into job_matches
                            (user_id, job_posting_id, match_score, verdict, covered_count, coverage,
                             input_hash, created_at, updated_at)
                        values (:userId, :postingId, cast(:score as decimal), :verdict, 1,
                                cast(:coverage as jsonb), :inputHash, now(), now())
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .param("score", score)
                .param("verdict", verdict)
                .param("coverage", "[{\"competencyId\":" + competencyId + ",\"isGap\":false}]")
                .param("inputHash", "test-input-hash-" + userId + "-" + postingId)
                .update();
    }

    private Long insertQuestion(Long postingId, int sequence, String promptText, Integer lengthLimit) {
        return jdbcClient.sql("""
                        insert into job_posting_questions
                            (job_posting_id, sequence, prompt_text, length_limit, created_at, updated_at)
                        values (:postingId, :sequence, :promptText, :lengthLimit, now(), now())
                        returning id
                        """)
                .param("postingId", postingId)
                .param("sequence", sequence)
                .param("promptText", promptText)
                .param("lengthLimit", lengthLimit, Types.INTEGER)
                .query(Long.class)
                .single();
    }
}
