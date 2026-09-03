package com.team.careerfit.job.dto;

import com.team.careerfit.job.entity.PostingStatus;
import com.team.careerfit.matching.entity.MatchVerdict;
import java.time.OffsetDateTime;
import java.util.List;

public record PostingListResponse(
        List<Item> items,
        int page,
        int size,
        long totalCount) {

    public record Item(
            Long id,
            String company,
            String position,
            OffsetDateTime deadline,
            PostingStatus status,
            boolean bookmarked,
            Match match,
            Essay essay) {
    }

    public record Match(
            int score,
            MatchVerdict verdict,
            List<String> coveredCompetencyNames,
            int requiredCount) {
    }

    public record Essay(
            EssayState state,
            int answered,
            int total) {
    }

    public enum EssayState {
        NO_QUESTIONS,
        EMPTY,
        WRITING,
        DONE
    }
}
