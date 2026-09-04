package com.team.careerlab.integration.ai.dto;

public record DraftResponse(String draft, int charCount, String promptVersion, String model) {
}
