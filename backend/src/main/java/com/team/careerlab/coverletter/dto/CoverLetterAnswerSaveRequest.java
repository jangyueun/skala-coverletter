package com.team.careerlab.coverletter.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CoverLetterAnswerSaveRequest(
        @NotNull(message = "본문을 입력해 주세요.") String content,
        @NotNull(message = "근거 경험 목록이 필요합니다.") List<Long> usedExperienceIds,
        /** AI 초안을 반영해 저장할 때만 값이 있다. 다음 저장에 없으면 이 답변의 출처가 다시 null 이 된다. */
        Long draftTaskId) {
}
