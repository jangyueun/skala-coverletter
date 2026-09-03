package com.team.careerfit.competency.dto;

import com.team.careerfit.competency.entity.CompetencyCategory;
import java.util.List;

public record CompetencyResponse(
        Long id,
        String name,
        CompetencyCategory category,
        List<String> aliases) {
}
