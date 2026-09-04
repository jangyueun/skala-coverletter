package com.team.careerlab.matching.entity;

import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 × 공고의 최신 매칭 결과. <b>(user, posting) 당 1행</b>이고 다시 계산하면 덮어쓴다.
 *
 * <p>이력을 남기지 않는다. 화면이 보여주는 건 현재 결과뿐이고, 경험을 고칠 때마다 활성 공고 전부를
 * 다시 계산하므로 이력을 쌓으면 금방 수십만 행이 된다.
 *
 * <p>{@code inputHash} 는 계산에 들어간 입력(경험 [id, competency_id, strength] 정렬 + 공고 [competency_id, weight] 정렬)의
 * sha256 이다. 모델·프롬프트 버전은 넣지 않는다 — 프롬프트를 올릴 때마다 전부 stale 이 되면 안 되기 때문이다.
 * 현재 입력의 해시와 다르면 stale 이고 서버가 MATCH 작업을 새로 만든다. 계산 중인지는 ai_tasks 에서 본다.
 *
 * <p>{@code matchScore} 는 0~1 로 저장하고 API 에서 0~100 으로 바꾼다.
 */
@Entity
@Table(
        name = "job_matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_matches_user_posting",
                columnNames = {"user_id", "job_posting_id"}))
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "match_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal matchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 32)
    private MatchVerdict verdict;

    /** 갭이 아닌 요구 역량 수. 목록 카드의 "8/14" 를 coverage 를 파싱하지 않고 내보내기 위한 중복 저장이다. */
    @Column(name = "covered_count", nullable = false)
    private int coveredCount;

    /**
     * 역량별 근거. 매칭 탭에서만 읽고 검색하지 않으므로 컬럼으로 풀지 않았다. 형식:
     * {@code [{"competencyId":3,"weight":0.9,"score":1.0,"isGap":false,"experiences":[{"id":1,"strength":0.8}]}]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coverage", nullable = false, columnDefinition = "jsonb")
    private String coverage;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 결과 반영 시각. v6 에서 computed_at 을 없애고 이 값으로 대신한다. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobMatch() {
        // JPA 용
    }

    private JobMatch(User user, JobPosting jobPosting, BigDecimal matchScore, int coveredCount, String coverage,
            String inputHash) {
        this.user = user;
        this.jobPosting = jobPosting;
        Instant now = Instant.now();
        this.createdAt = now;
        apply(matchScore, coveredCount, coverage, inputHash, now);
    }

    /** 첫 계산 결과. 판정은 점수에서 유도한다 — AI 응답의 verdict 를 그대로 믿지 않는다. */
    public static JobMatch compute(User user, JobPosting jobPosting, BigDecimal matchScore, int coveredCount,
            String coverage, String inputHash) {
        return new JobMatch(user, jobPosting, matchScore, coveredCount, coverage, inputHash);
    }

    /** 재계산 결과로 덮어쓴다. 행은 그대로 두고 값만 바꾼다. */
    public void recompute(BigDecimal matchScore, int coveredCount, String coverage, String inputHash) {
        apply(matchScore, coveredCount, coverage, inputHash, Instant.now());
    }

    private void apply(BigDecimal matchScore, int coveredCount, String coverage, String inputHash, Instant at) {
        this.matchScore = matchScore;
        this.verdict = MatchVerdict.from(matchScore);
        this.coveredCount = coveredCount;
        this.coverage = coverage;
        this.inputHash = inputHash;
        this.updatedAt = at;
    }

    /** 입력이 바뀌었으면 stale 이다. 호출 쪽은 stale 이면 MATCH 작업을 만들고 그동안 기존 결과를 보여준다. */
    public boolean isStale(String currentInputHash) {
        return !inputHash.equals(currentInputHash);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public MatchVerdict getVerdict() {
        return verdict;
    }

    public int getCoveredCount() {
        return coveredCount;
    }

    public String getCoverage() {
        return coverage;
    }

    public String getInputHash() {
        return inputHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
