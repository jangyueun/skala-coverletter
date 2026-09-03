package com.team.careerfit.job.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PostingQuestionResponse(
        Long id,
        int sequence,
        String promptText,
        Integer lengthLimit,
        Answer answer) {

    public record Answer(
            String content,
            int charCount,
            List<Long> usedExperienceIds,
            OffsetDateTime updatedAt) {
    }
}
