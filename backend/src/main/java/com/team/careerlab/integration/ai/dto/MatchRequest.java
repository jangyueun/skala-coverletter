package com.team.careerlab.integration.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record MatchRequest(MatchPosting posting, List<MatchExperience> experiences) {

    public record MatchPosting(Long id, List<MatchRequirement> required) {
    }

    public record MatchRequirement(Long competencyId, BigDecimal weight, String evidenceLine) {
    }

    public record MatchExperience(Long id, String title, String result, List<ExperienceStrength> competencies) {
    }

    public record ExperienceStrength(Long competencyId, BigDecimal strength) {
    }
}
