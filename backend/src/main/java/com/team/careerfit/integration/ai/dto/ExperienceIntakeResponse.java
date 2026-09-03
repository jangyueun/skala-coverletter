package com.team.careerfit.integration.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record ExperienceIntakeResponse(List<Candidate> candidates, String promptVersion, String model) {

    public record Candidate(
            String key,
            String title,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            String situation,
            String action,
            List<Question> questions,
            List<Long> suggestedCompetencyIds,
            Long duplicateOfExperienceId) {
    }

    public record Question(String field, String q, String why) {
    }
}
