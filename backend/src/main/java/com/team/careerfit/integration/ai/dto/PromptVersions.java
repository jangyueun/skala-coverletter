package com.team.careerfit.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /ai/prompts/versions} 만 명세대로 snake_case JSON 키를 쓴다. */
public record PromptVersions(
        @JsonProperty("posting_analysis") String postingAnalysis,
        @JsonProperty("experience_intake") String experienceIntake,
        String match,
        String draft) {
}
