package com.team.careerfit.integration.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record MatchResponse(BigDecimal overall, String verdict, List<MatchRow> rows, String promptVersion,
        String model) {

    public record MatchRow(Long competencyId, BigDecimal weight, BigDecimal score, boolean isGap,
            List<Long> experienceIds) {
    }
}
