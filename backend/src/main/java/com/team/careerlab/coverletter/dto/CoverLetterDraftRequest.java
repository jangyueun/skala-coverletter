package com.team.careerlab.coverletter.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CoverLetterDraftRequest(
        @NotEmpty(message = "근거로 쓸 경험을 1개 이상 선택해 주세요.") List<Long> experienceIds) {
}
