package com.team.careerfit.coverletter.dto;

import com.team.careerfit.coverletter.entity.CoverLetterAnswer;
import java.time.Instant;
import java.util.List;

public record CoverLetterAnswerResponse(
        Long questionId,
        String content,
        int charCount,
        List<Long> usedExperienceIds,
        Long aiTaskId,
        Instant updatedAt) {

    public static CoverLetterAnswerResponse from(Long questionId, CoverLetterAnswer answer) {
        return new CoverLetterAnswerResponse(questionId, answer.getContent(), answer.getCharCount(),
                answer.getUsedExperienceIds(), answer.getAiTaskId(), answer.getUpdatedAt());
    }
}
