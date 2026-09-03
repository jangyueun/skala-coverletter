package com.team.careerfit.experience.entity;

import com.team.careerfit.competency.entity.Competency;
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
 * 경험에 붙은 역량과 강도. 경험 관리의 역량 칩 필터가 competency_id 로 이 테이블을 조회한다.
 *
 * <p>강도는 약 0.4 · 중 0.7 · 강 0.9 세 단계지만 컬럼은 0~1 소수다. 매칭 점수 계산에서
 * 그대로 곱하기 때문에 단계 이름이 아니라 숫자를 저장한다.
 *
 * <p>생성은 {@link Experience#replaceCompetencies} 를 통해서만 한다 — 경험 없이 존재할 수 없는 자식이다.
 */
@Entity
@Table(name = "experience_competencies")
public class ExperienceCompetency {

    @EmbeddedId
    private ExperienceCompetencyId id;

    @MapsId("experienceId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experience_id", nullable = false)
    private Experience experience;

    @MapsId("competencyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    @Column(name = "strength", nullable = false, precision = 3, scale = 2)
    private BigDecimal strength;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExperienceCompetency() {
        // JPA 용
    }

    ExperienceCompetency(Experience experience, Competency competency, BigDecimal strength) {
        this.id = new ExperienceCompetencyId(experience.getId(), competency.getId());
        this.experience = experience;
        this.competency = competency;
        this.strength = strength;
        this.createdAt = Instant.now();
    }

    void changeStrength(BigDecimal strength) {
        this.strength = strength;
    }

    public ExperienceCompetencyId getId() {
        return id;
    }

    public Experience getExperience() {
        return experience;
    }

    public Competency getCompetency() {
        return competency;
    }

    public BigDecimal getStrength() {
        return strength;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
