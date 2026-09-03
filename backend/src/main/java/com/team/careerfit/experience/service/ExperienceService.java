package com.team.careerfit.experience.service;

import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCompetency;
import com.team.careerfit.experience.repository.ExperienceCompetencyRepository;
import com.team.careerfit.experience.repository.ExperienceRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceService {

    private final ExperienceRepository experiences;
    private final ExperienceCompetencyRepository experienceCompetencies;

    public ExperienceService(ExperienceRepository experiences, ExperienceCompetencyRepository experienceCompetencies) {
        this.experiences = experiences;
        this.experienceCompetencies = experienceCompetencies;
    }

    /** competencyId 가 없으면 전체, 있으면 그 역량이 붙은 경험만 돌려준다. */
    @Transactional(readOnly = true)
    public List<ExperienceResponse> list(Long userId, Long competencyId) {
        List<Experience> found = competencyId == null
                ? experiences.findByUserIdOrderByStartDateDesc(userId)
                : experiences.findByUserIdAndCompetencyId(userId, competencyId);

        if (found.isEmpty()) {
            return List.of();
        }

        List<Long> experienceIds = found.stream().map(Experience::getId).toList();

        Map<Long, List<ExperienceCompetency>> competenciesByExperience = experienceCompetencies
                .findByExperienceIdInFetchCompetency(experienceIds).stream()
                .collect(Collectors.groupingBy(ec -> ec.getExperience().getId()));

        Map<Long, Long> usedCounts = experiences.countUsedInQuestions(userId).stream()
                .collect(Collectors.toMap(ExperienceRepository.UsedCount::getExperienceId,
                        ExperienceRepository.UsedCount::getUsedCount));

        return found.stream()
                .map(e -> ExperienceResponse.of(e,
                        competenciesByExperience.getOrDefault(e.getId(), List.of()),
                        usedCounts.getOrDefault(e.getId(), 0L)))
                .toList();
    }
}
