package com.team.careerlab.job.entity;

import com.team.careerlab.competency.entity.Competency;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 공고가 요구하는 역량 하나. AI 분석(POSTING_ANALYSIS)이 만든다.
 *
 * <p>홈의 역량 다중 필터, 상세의 연관 태그, 관련 공고(태그 겹침 수)가 모두 이 테이블을 읽는다.
 * {@code evidenceLine} 은 공고 원문 문장 그대로다 — 매칭 표의 "공고 근거" 칸에 그대로 나간다.
 *
 * <p>생성은 {@link JobPosting#replaceRequiredCompetencies} 를 통해서만 한다.
 */
@Entity
@Table(name = "posting_competencies")
public class PostingCompetency {

    @EmbeddedId
    private PostingCompetencyId id;

    @MapsId("jobPostingId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @MapsId("competencyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    /** AI 가 매긴 중요도 0.5~1.0. CHECK 는 0~1 로 느슨하게 잡았다 — 프롬프트가 바뀌어도 스키마를 안 건드리게. */
    @Column(name = "weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal weight;

    @Column(name = "evidence_line", nullable = false, columnDefinition = "text")
    private String evidenceLine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PostingCompetency() {
        // JPA 용
    }

    PostingCompetency(JobPosting jobPosting, Competency competency, BigDecimal weight, String evidenceLine) {
        this.id = new PostingCompetencyId(jobPosting.getId(), competency.getId());
        this.jobPosting = jobPosting;
        this.competency = competency;
        this.weight = weight;
        this.evidenceLine = evidenceLine;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    void reassess(BigDecimal weight, String evidenceLine) {
        this.weight = weight;
        this.evidenceLine = evidenceLine;
        this.updatedAt = Instant.now();
    }

    public PostingCompetencyId getId() {
        return id;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public Competency getCompetency() {
        return competency;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public String getEvidenceLine() {
        return evidenceLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
