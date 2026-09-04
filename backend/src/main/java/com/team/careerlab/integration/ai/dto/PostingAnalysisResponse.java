package com.team.careerlab.integration.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record PostingAnalysisResponse(List<RequiredCompetency> required, String promptVersion, String model) {

    public record RequiredCompetency(Long competencyId, BigDecimal weight, String evidence) {
    }
}
