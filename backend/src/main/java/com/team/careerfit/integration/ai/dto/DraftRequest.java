package com.team.careerfit.integration.ai.dto;

import java.util.List;

public record DraftRequest(DraftQuestion question, DraftPosting posting, List<DraftExperience> experiences) {

    public record DraftQuestion(String promptText, Integer lengthLimit) {
    }

    public record DraftPosting(String company, String position, List<String> requiredNames) {
    }

    public record DraftExperience(String title, String situation, String task, String action, String result) {
    }
}
