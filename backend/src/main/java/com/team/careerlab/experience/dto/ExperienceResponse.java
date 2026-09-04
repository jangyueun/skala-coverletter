package com.team.careerlab.experience.dto;

import com.team.careerlab.experience.entity.Experience;
import com.team.careerlab.experience.entity.ExperienceCategory;
import com.team.careerlab.experience.entity.ExperienceCompetency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExperienceResponse(
        Long id,
        String title,
        ExperienceCategory category,
        LocalDate startDate,
        LocalDate endDate,
        String situation,
        String task,
        String action,
        String result,
        Long aiTaskId,
        List<CompetencyItem> competencies,
        long usedInQuestions) {

    public static ExperienceResponse of(Experience experience, List<ExperienceCompetency> competencies,
            long usedInQuestions) {
        return new ExperienceResponse(
                experience.getId(),
                experience.getTitle(),
                experience.getCategory(),
                experience.getStartDate(),
                experience.getEndDate(),
                experience.getSituation(),
                experience.getTask(),
                experience.getAction(),
                experience.getResult(),
                experience.getAiTaskId(),
                competencies.stream().map(CompetencyItem::from).toList(),
                usedInQuestions);
    }

    public record CompetencyItem(Long competencyId, String name, BigDecimal strength) {

        public static CompetencyItem from(ExperienceCompetency ec) {
            return new CompetencyItem(ec.getCompetency().getId(), ec.getCompetency().getName(), ec.getStrength());
        }
    }
}
