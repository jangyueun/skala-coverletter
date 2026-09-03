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

    /** 아직 답변을 저장한 적 없으면 빈 값으로 채운다. */
    public static CoverLetterAnswerResponse empty(Long questionId) {
        return new CoverLetterAnswerResponse(questionId, "", 0, List.of(), null, null);
    }
}
