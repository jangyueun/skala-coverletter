package com.team.careerlab.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** posting_competencies 의 복합 키 (job_posting_id, competency_id). 한 공고에 같은 역량은 한 번만 붙는다. */
@Embeddable
public class PostingCompetencyId implements Serializable {

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "competency_id", nullable = false)
    private Long competencyId;

    protected PostingCompetencyId() {
        // JPA 용
    }

    public PostingCompetencyId(Long jobPostingId, Long competencyId) {
        this.jobPostingId = jobPostingId;
        this.competencyId = competencyId;
    }

    public Long getJobPostingId() {
        return jobPostingId;
    }

    public Long getCompetencyId() {
        return competencyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostingCompetencyId that)) {
            return false;
        }
        return Objects.equals(jobPostingId, that.jobPostingId) && Objects.equals(competencyId, that.competencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobPostingId, competencyId);
    }
}
