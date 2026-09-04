package com.team.careerlab.competency.dto;

import com.team.careerlab.competency.entity.CompetencyCategory;
import java.util.List;

public record CompetencyResponse(
        Long id,
        String name,
        CompetencyCategory category,
        List<String> aliases) {
}
