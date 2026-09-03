package com.team.careerfit.integration.ai.dto;

import java.util.List;

/** 역량 사전 한 줄. posting-analysis·experience-intake 요청에 공통으로 실린다. */
public record AiCompetency(Long id, String name, String category, List<String> aliases) {
}
