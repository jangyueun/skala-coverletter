package com.team.careerlab.integration.ai.dto;

import java.util.List;

public record PostingAnalysisRequest(Long postingId, String content, List<AiCompetency> competencies) {
}
