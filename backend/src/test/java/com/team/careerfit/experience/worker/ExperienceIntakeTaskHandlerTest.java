package com.team.careerfit.experience.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.worker.AiTaskHandler;
import com.team.careerfit.competency.dto.CompetencyResponse;
import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.competency.service.CompetencyService;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCategory;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.integration.ai.client.AiProviderClient;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeRequest;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeResponse;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeResponse.Candidate;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeResponse.Question;
import com.team.careerfit.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ExperienceIntakeTaskHandlerTest {

    private final AiProviderClient aiClient = mock(AiProviderClient.class);
    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final CompetencyService competencies = mock(CompetencyService.class);
    private final ExperienceIntakeTaskHandler handler = new ExperienceIntakeTaskHandler(
            aiClient, experiences, competencies, new JsonMapper());

    @Test
    void 링크_기존_경험_사전을_붙여_보내고_후보를_result_로_남긴다() {
        User me = User.firstLogin("T", "U7", "지호", null, null);
        Experience existing = Experience.register(me, "MSA 구축", ExperienceCategory.TEAM_PROJECT,
                LocalDate.of(2026, 8, 1), null, null, null, null, "결과", null);
        setId(existing, 1L);
        when(experiences.findByUserIdOrderByStartDateDesc(7L)).thenReturn(List.of(existing));
        when(competencies.findAll(isNull())).thenReturn(List.of(
                new CompetencyResponse(3L, "API 설계·연동", CompetencyCategory.ROLE, List.of("REST"))));
        Candidate candidate = new Candidate("repo", "새 프로젝트", "PERSONAL_PROJECT", LocalDate.of(2026, 6, 1), null,
                "상황", "행동", List.of(new Question("result", "수치는?", "코드에 없다")), List.of(3L), null);
        when(aiClient.experienceIntake(any())).thenReturn(
                new ExperienceIntakeResponse(List.of(candidate), "experience_intake/v1", "claude-opus-5"));

        AiTask task = AiTask.experienceIntake(7L, "key", "hash",
                "{\"links\":[\"https://github.com/me/repo\"],\"fileUrls\":[]}");
        AiTaskHandler.Result result = handler.handle(task);

        ArgumentCaptor<ExperienceIntakeRequest> sent = ArgumentCaptor.forClass(ExperienceIntakeRequest.class);
        verify(aiClient).experienceIntake(sent.capture());
        ExperienceIntakeRequest request = sent.getValue();
        assertThat(request.links()).containsExactly("https://github.com/me/repo");
        assertThat(request.fileUrls()).isEmpty();
        assertThat(request.existingExperiences()).hasSize(1);
        assertThat(request.existingExperiences().get(0).category()).isEqualTo("TEAM_PROJECT");
        assertThat(request.competencies()).hasSize(1);
        assertThat(request.competencies().get(0).category()).isEqualTo("ROLE");

        assertThat(result.promptVersion()).isEqualTo("experience_intake/v1");
        assertThat(result.resultPayload()).contains("\"candidates\":[")
                .contains("\"key\":\"repo\"")
                .contains("\"suggestedCompetencyIds\":[3]")
                .contains("\"startDate\":\"2026-06-01\"");
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
