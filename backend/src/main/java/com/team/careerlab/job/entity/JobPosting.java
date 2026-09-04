package com.team.careerlab.job.entity;

import com.team.careerlab.competency.entity.Competency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 채용 공고. 목록·상세·매칭·자소서 문항의 뿌리다.
 *
 * <p>직무 계열 컬럼이 없다. "비슷한 직무"는 요구 역량 태그가 얼마나 겹치는지로 계산한다(v6 회의 결정).
 *
 * <p>{@code deadline} 은 시각까지 있다("2026-09-12 18:00 마감"). D-day 와 마감 판정은 날짜가 아니라 시각으로 비교한다.
 * {@code analyzedAt} 이 null 이면 AI 분석 전이라 요구 역량이 없고, 화면은 매칭률을 보여주지 않는다.
 */
@Entity
@Table(
        name = "job_postings",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_postings_source_url", columnNames = "source_url"),
        indexes = {
                @Index(name = "ix_job_postings_status_deadline", columnList = "status, deadline"),
                @Index(name = "ix_job_postings_company_deadline", columnList = "company_id, deadline")
        })
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "position", nullable = false, length = 200)
    private String position;

    /** 원문. 목록 DTO 에서는 제외한다 — 공고 하나가 수천 자라 목록에 실으면 응답이 무거워진다. */
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PostingStatus status;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 분석이 끝나면 통째로 교체되는 자식이라 cascade · orphanRemoval 을 건다. */
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostingCompetency> requiredCompetencies = new ArrayList<>();

    protected JobPosting() {
        // JPA 용
    }

    private JobPosting(Company company, String position, String content, String sourceUrl, Instant deadline) {
        this.company = company;
        this.position = position;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.deadline = deadline;
        this.status = PostingStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 수집된 공고. 요구 역량은 비어 있고 {@code analyzedAt} 도 null 이다 — 분석 작업이 채운다. */
    public static JobPosting collect(Company company, String position, String content, String sourceUrl,
            Instant deadline) {
        return new JobPosting(company, position, content, sourceUrl, deadline);
    }

    /** 요구 역량의 근거와 가중치. AI 응답 한 줄에 해당한다. */
    public record Requirement(BigDecimal weight, String evidenceLine) {
    }

    /**
     * 분석 결과로 요구 역량을 통째로 바꾸고 {@code analyzedAt} 을 찍는다.
     *
     * <p>{@link com.team.careerlab.experience.entity.Experience#replaceCompetencies} 와 같은 이유로
     * 비우고 다시 넣지 않는다 — 같은 키를 한 트랜잭션에서 지우고 넣으면 PK 충돌이 난다.
     */
    public void replaceRequiredCompetencies(Map<Competency, Requirement> requirements) {
        Map<Long, Requirement> byId = new HashMap<>();
        requirements.forEach((competency, requirement) -> byId.put(competency.getId(), requirement));

        requiredCompetencies.removeIf(existing -> !byId.containsKey(existing.getCompetency().getId()));
        for (Map.Entry<Competency, Requirement> entry : requirements.entrySet()) {
            Requirement requirement = entry.getValue();
            requiredCompetencies.stream()
                    .filter(existing -> existing.getCompetency().getId().equals(entry.getKey().getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.reassess(requirement.weight(), requirement.evidenceLine()),
                            () -> requiredCompetencies.add(new PostingCompetency(
                                    this, entry.getKey(), requirement.weight(), requirement.evidenceLine())));
        }
        Instant now = Instant.now();
        this.analyzedAt = now;
        this.updatedAt = now;
    }

    /** 마감 배치가 부른다. */
    public void close() {
        this.status = PostingStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    /** 배치가 아직 안 돌았어도 시각이 지났으면 마감이다. 화면의 "마감됨" 판정은 이 메서드를 쓴다. */
    public boolean isClosed(Instant now) {
        return status == PostingStatus.CLOSED || !deadline.isAfter(now);
    }

    public boolean isAnalyzed() {
        return analyzedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public String getContent() {
        return content;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public PostingStatus getStatus() {
        return status;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PostingCompetency> getRequiredCompetencies() {
        return Collections.unmodifiableList(requiredCompetencies);
    }
}
