package com.team.careerfit.job.dto;

import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.matching.entity.MatchVerdict;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PostingMatchResponse(
        MatchStatus status,
        Long taskId,
        Integer score,
        MatchVerdict verdict,
        Integer coveredCount,
        int requiredCount,
        OffsetDateTime updatedAt,
        List<Row> rows) {

    public enum MatchStatus {
        NOT_COMPUTED,
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record Row(
            Long competencyId,
            String name,
            CompetencyCategory category,
            BigDecimal weight,
            BigDecimal score,
            boolean isGap,
            String evidenceLine,
            List<Experience> experiences) {
    }

    public record Experience(
            Long id,
            String title,
            String result,
            BigDecimal strength) {
    }
}
