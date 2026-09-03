package com.team.careerfit.competency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.careerfit.global.security.SessionKeys;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
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
class CompetencyControllerTest {

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
    void 역량사전을_범주와_별칭을_포함해_조회한다() throws Exception {
        Long roleId = insertCompetency("API 설계·연동 테스트", "ROLE");
        Long techId = insertCompetency("Spring Boot 테스트", "TECH");
        insertAlias(techId, "Spring");
        insertAlias(techId, "스프링부트");
        MockHttpSession session = loginSession();

        mockMvc.perform(get("/api/competencies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + roleId + ")].category").value("ROLE"))
                .andExpect(jsonPath("$[?(@.id == " + techId + ")].aliases.length()").value(2));

        mockMvc.perform(get("/api/competencies").session(session).queryParam("category", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(techId))
                .andExpect(jsonPath("$[0].aliases[0]").value("Spring"))
                .andExpect(jsonPath("$[0].aliases[1]").value("스프링부트"));
    }

    @Test
    void 잘못된_범주는_400을_응답한다() throws Exception {
        mockMvc.perform(get("/api/competencies")
                        .session(loginSession())
                        .queryParam("category", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 로그인하지_않으면_역량사전을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/competencies"))
                .andExpect(status().isUnauthorized());
    }

    private Long insertCompetency(String name, String category) {
        return jdbcClient.sql("""
                        insert into competencies (name, category, created_at, updated_at)
                        values (:name, :category, now(), now())
                        returning id
                        """)
                .param("name", name)
                .param("category", category)
                .query(Long.class)
                .single();
    }

    private void insertAlias(Long competencyId, String alias) {
        jdbcClient.sql("""
                        insert into competency_aliases (competency_id, alias, created_at)
                        values (:competencyId, :alias, now())
                        """)
                .param("competencyId", competencyId)
                .param("alias", alias)
                .update();
    }

    private MockHttpSession loginSession() {
        String uniqueId = "U_COMPETENCY_" + System.nanoTime();
        User user = users.save(User.firstLogin(
                "T_TEST",
                uniqueId,
                "역량사전 사용자",
                uniqueId.toLowerCase() + "@example.com",
                null));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        return session;
    }
}
