package com.team.careerfit.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.careerfit.competency.entity.Competency;
import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCategory;
import com.team.careerfit.experience.repository.ExperienceCompetencyRepository;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperienceServiceTest {

    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final ExperienceCompetencyRepository experienceCompetencies = mock(ExperienceCompetencyRepository.class);
    private final ExperienceService service = new ExperienceService(experiences, experienceCompetencies);

    @Test
    void 역량과_사용_건수를_경험에_합쳐서_돌려준다() {
        User user = User.firstLogin("T1", "U1", "지호", null, null);
        setId(user, 7L);
        Experience experience = Experience.register(user, "MSA 주문 서비스", ExperienceCategory.TEAM_PROJECT,
                null, null, "s", "t", "a", "r", null);
        setId(experience, 1L);
        Competency competency = Competency.of("API 설계·연동", CompetencyCategory.ROLE);
        setId(competency, 4L);
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
