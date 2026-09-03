package com.team.careerfit.job.dto;

import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.job.entity.PostingStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PostingDetailResponse(
        Long id,
        String company,
        String position,
        OffsetDateTime deadline,
        PostingStatus status,
        String sourceUrl,
        String content,
        boolean bookmarked,
        List<RequiredCompetency> requiredCompetencies,
        Related related) {

    public record RequiredCompetency(
            Long competencyId,
            String name,
            CompetencyCategory category,
            BigDecimal weight,
            String evidenceLine) {
    }

    public record Related(
            List<SameCompany> sameCompany,
            List<Similar> similar) {
    }

    public record SameCompany(Long id, String position, Integer score) {
    }

    public record Similar(
            Long id,
            String company,
            String position,
            int sharedCompetencyCount,
            Integer score) {
    }
}
