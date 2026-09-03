package com.team.careerfit.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.service.AiTaskService;
import com.team.careerfit.competency.entity.Competency;
import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.competency.repository.CompetencyRepository;
import com.team.careerfit.experience.dto.ExperienceCreateRequest;
import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCategory;
import com.team.careerfit.experience.exception.ExperienceException;
import com.team.careerfit.experience.repository.ExperienceCompetencyRepository;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.job.service.JobPostingService;
import com.team.careerfit.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperienceServiceTest {

    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final ExperienceCompetencyRepository experienceCompetencies = mock(ExperienceCompetencyRepository.class);
    private final CompetencyRepository competencies = mock(CompetencyRepository.class);
    private final JobPostingService jobPostings = mock(JobPostingService.class);
    private final AiTaskService aiTasks = mock(AiTaskService.class);
    private final ExperienceService service = new ExperienceService(experiences, experienceCompetencies,
            competencies, jobPostings, aiTasks);

    private final User user = user(7L);
    private final Competency competency = competency(4L, "API 설계·연동");

    @Test
    void 역량과_사용_건수를_경험에_합쳐서_돌려준다() {
        Experience experience = Experience.register(user, "MSA 주문 서비스", ExperienceCategory.TEAM_PROJECT,
                null, null, "s", "t", "a", "r", null);
        setId(experience, 1L);
        experience.replaceCompetencies(Map.of(competency, new BigDecimal("0.80")));

        when(experiences.findByUserIdOrderByStartDateDesc(7L)).thenReturn(List.of(experience));
        when(experienceCompetencies.findByExperienceIdInFetchCompetency(List.of(1L)))
                .thenReturn(experience.getCompetencies());
        when(experiences.countUsedInQuestions(7L)).thenReturn(List.of(usedCount(1L, 2L)));

        List<ExperienceResponse> result = service.list(7L, null);

        assertThat(result).hasSize(1);
        ExperienceResponse response = result.get(0);
        assertThat(response.usedInQuestions()).isEqualTo(2L);
        assertThat(response.competencies()).extracting(ExperienceResponse.CompetencyItem::name)
                .containsExactly("API 설계·연동");
    }

    @Test
    void 경험이_없으면_추가_조회_없이_빈_목록을_돌려준다() {
        when(experiences.findByUserIdOrderByStartDateDesc(7L)).thenReturn(List.of());

        List<ExperienceResponse> result = service.list(7L, null);

        assertThat(result).isEmpty();
    }

    @Test
    void 저장하면_활성_공고마다_MATCH_작업을_만든다() {
        when(competencies.findAllById(List.of(4L))).thenReturn(List.of(competency));
        when(experiences.save(any(Experience.class))).thenAnswer(invocation -> {
            Experience experience = invocation.getArgument(0);
            setId(experience, 1L);
            return experience;
        });
        when(jobPostings.findActivePostingIds()).thenReturn(List.of(10L, 11L));
        when(aiTasks.createMatchTask(eq(7L), eq(10L), anyString())).thenReturn(taskWithId(801L));
        when(aiTasks.createMatchTask(eq(7L), eq(11L), anyString())).thenReturn(taskWithId(802L));

        ExperienceSaveResponse response = service.register(user, request());

        assertThat(response.experience().id()).isEqualTo(1L);
        assertThat(response.experience().competencies()).extracting(c -> c.name()).containsExactly("API 설계·연동");
        assertThat(response.reassess().postingCount()).isEqualTo(2);
        assertThat(response.reassess().taskIds()).containsExactly(801L, 802L);
    }

    @Test
    void 존재하지_않는_역량이_섞여_있으면_거부한다() {
        when(competencies.findAllById(List.of(4L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.register(user, request()))
                .isInstanceOf(ExperienceException.class);
    }

    @Test
    void 종료일이_시작일보다_빠르면_거부한다() {
        ExperienceCreateRequest invalid = new ExperienceCreateRequest("제목", ExperienceCategory.TEAM_PROJECT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), null, null, null, "결과",
                List.of(new ExperienceCreateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))), null);

        assertThatThrownBy(() -> service.register(user, invalid))
                .isInstanceOf(ExperienceException.class);
    }

    private ExperienceCreateRequest request() {
        return new ExperienceCreateRequest("MSA 주문 서비스", ExperienceCategory.TEAM_PROJECT, null, null, "s", "t", "a",
                "r", List.of(new ExperienceCreateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))), null);
    }

    private static ExperienceRepository.UsedCount usedCount(Long experienceId, Long count) {
        return new ExperienceRepository.UsedCount() {
            @Override
            public Long getExperienceId() {
                return experienceId;
            }

            @Override
            public Long getUsedCount() {
                return count;
            }
        };
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
