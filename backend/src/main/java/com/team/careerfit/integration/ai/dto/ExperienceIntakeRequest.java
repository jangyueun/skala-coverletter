package com.team.careerfit.integration.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record ExperienceIntakeRequest(
        List<String> links,
        List<String> fileUrls,
        List<ExistingExperience> existingExperiences,
        List<AiCompetency> competencies) {

    public record ExistingExperience(Long id, String title, String category, LocalDate startDate, LocalDate endDate) {
    }
}
