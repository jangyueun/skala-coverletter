package com.team.careerlab.competency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 역량 별칭. 공고 원문의 표현("트러블슈팅", "RCA")을 표준 역량으로 잇는다.
 *
 * <p><b>별칭 하나는 표준 역량 하나에만 속한다</b>(alias UNIQUE). 두 역량이 같은 별칭을 가지면
 * AI 분석 결과가 어느 쪽으로 붙을지 정해지지 않는다. 별칭은 AI 분석 프롬프트에 사전 입력으로 들어간다.
 */
@Entity
@Table(
        name = "competency_aliases",
        uniqueConstraints = @UniqueConstraint(name = "uk_competency_aliases_alias", columnNames = "alias"))
public class CompetencyAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompetencyAlias() {
        // JPA 용
    }

    private CompetencyAlias(Competency competency, String alias) {
        this.competency = competency;
        this.alias = alias;
        this.createdAt = Instant.now();
    }

    public static CompetencyAlias of(Competency competency, String alias) {
        return new CompetencyAlias(competency, alias);
    }

    public Long getId() {
        return id;
    }

    public Competency getCompetency() {
        return competency;
    }

    public String getAlias() {
        return alias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
