package com.team.careerfit.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.service.AiTaskService;
import com.team.careerfit.competency.entity.Competency;
import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.competency.repository.CompetencyRepository;
import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.dto.ExperienceUpdateRequest;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCategory;
import com.team.careerfit.experience.exception.ExperienceException;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.job.service.JobPostingService;
import com.team.careerfit.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExperienceServiceTest {

    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final CompetencyRepository competencies = mock(CompetencyRepository.class);
    private final JobPostingService jobPostings = mock(JobPostingService.class);
    private final AiTaskService aiTasks = mock(AiTaskService.class);
    private final ExperienceService service = new ExperienceService(experiences, competencies, jobPostings, aiTasks);

    private final User owner = user(7L);
    private final Competency competency = competency(4L, "API 설계·연동");

    @Test
    void 소유자가_수정하면_역량과_매칭_작업이_갱신된다() {
        Experience experience = experience(owner, 1L);
        when(experiences.findById(1L)).thenReturn(Optional.of(experience));
        when(competencies.findAllById(List.of(4L))).thenReturn(List.of(competency));
        when(jobPostings.findActivePostingIds()).thenReturn(List.of(10L));
        when(aiTasks.createMatchTask(eq(7L), eq(10L), anyString())).thenReturn(taskWithId(801L));

        ExperienceSaveResponse response = service.update(owner, 1L, request());

        assertThat(response.experience().title()).isEqualTo("MSA 주문 서비스 v2");
        assertThat(response.experience().competencies()).extracting(c -> c.name()).containsExactly("API 설계·연동");
        assertThat(response.reassess().taskIds()).containsExactly(801L);
    }

    @Test
    void 존재하지_않는_경험이면_거부한다() {
        when(experiences.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(owner, 1L, request()))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(competencies, never()).findAllById(anyList());
    }

    @Test
    void 다른_사용자의_경험이면_거부한다() {
        User owner2 = user(99L);
        Experience experience = experience(owner2, 1L);
        when(experiences.findById(1L)).thenReturn(Optional.of(experience));

        assertThatThrownBy(() -> service.update(owner, 1L, request()))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ExperienceUpdateRequest request() {
        return new ExperienceUpdateRequest("MSA 주문 서비스 v2", ExperienceCategory.TEAM_PROJECT, null, null, "s", "t",
                "a", "r", List.of(new ExperienceUpdateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))));
    }

    private static Experience experience(User user, Long id) {
        Experience experience = Experience.register(user, "MSA 주문 서비스", ExperienceCategory.TEAM_PROJECT, null, null,
                "s", "t", "a", "r", null);
        setId(experience, id);
        return experience;
    }

    private static AiTask taskWithId(Long id) {
        AiTask task = AiTask.match(7L, 10L, "key" + id, "hash", "{}");
        setId(task, id);
        return task;
    }

    private static User user(Long id) {
        User user = User.firstLogin("T1", "U1", "지호", null, null);
        setId(user, id);
        return user;
    }

    private static Competency competency(Long id, String name) {
        Competency competency = Competency.of(name, CompetencyCategory.ROLE);
        setId(competency, id);
        return competency;
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
