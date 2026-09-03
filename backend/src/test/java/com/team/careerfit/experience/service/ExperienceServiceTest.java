package com.team.careerfit.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.team.careerfit.experience.dto.ExperienceCreateRequest;
import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.dto.ExperienceUpdateRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

        ExperienceSaveResponse response = service.register(user, createRequest());

        assertThat(response.experience().id()).isEqualTo(1L);
        assertThat(response.experience().competencies()).extracting(c -> c.name()).containsExactly("API 설계·연동");
        assertThat(response.reassess().postingCount()).isEqualTo(2);
        assertThat(response.reassess().taskIds()).containsExactly(801L, 802L);
    }

    @Test
    void 등록_시_존재하지_않는_역량이_섞여_있으면_거부한다() {
        when(competencies.findAllById(List.of(4L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.register(user, createRequest()))
                .isInstanceOf(ExperienceException.class);
    }

    @Test
    void 등록_시_종료일이_시작일보다_빠르면_거부한다() {
        ExperienceCreateRequest invalid = new ExperienceCreateRequest("제목", ExperienceCategory.TEAM_PROJECT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), null, null, null, "결과",
                List.of(new ExperienceCreateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))), null);

        assertThatThrownBy(() -> service.register(user, invalid))
                .isInstanceOf(ExperienceException.class);
    }

    @Test
    void 소유자가_수정하면_역량과_매칭_작업이_갱신된다() {
        Experience experience = experience(user, 1L);
        when(experiences.findById(1L)).thenReturn(Optional.of(experience));
        when(competencies.findAllById(List.of(4L))).thenReturn(List.of(competency));
        when(jobPostings.findActivePostingIds()).thenReturn(List.of(10L));
        when(aiTasks.createMatchTask(eq(7L), eq(10L), anyString())).thenReturn(taskWithId(801L));

        ExperienceSaveResponse response = service.update(user, 1L, updateRequest());

        assertThat(response.experience().title()).isEqualTo("MSA 주문 서비스 v2");
        assertThat(response.experience().competencies()).extracting(c -> c.name()).containsExactly("API 설계·연동");
        assertThat(response.reassess().taskIds()).containsExactly(801L);
    }

    @Test
    void 수정_시_존재하지_않는_경험이면_거부한다() {
        when(experiences.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(user, 1L, updateRequest()))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(competencies, never()).findAllById(anyList());
    }

    @Test
    void 수정_시_다른_사용자의_경험이면_거부한다() {
        User otherOwner = user(99L);
        Experience experience = experience(otherOwner, 1L);
        when(experiences.findById(1L)).thenReturn(Optional.of(experience));

        assertThatThrownBy(() -> service.update(user, 1L, updateRequest()))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ExperienceCreateRequest createRequest() {
        return new ExperienceCreateRequest("MSA 주문 서비스", ExperienceCategory.TEAM_PROJECT, null, null, "s", "t", "a",
                "r", List.of(new ExperienceCreateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))), null);
    }

    private ExperienceUpdateRequest updateRequest() {
        return new ExperienceUpdateRequest("MSA 주문 서비스 v2", ExperienceCategory.TEAM_PROJECT, null, null, "s", "t",
                "a", "r", List.of(new ExperienceUpdateRequest.CompetencyStrength(4L, new BigDecimal("0.8"))));
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
