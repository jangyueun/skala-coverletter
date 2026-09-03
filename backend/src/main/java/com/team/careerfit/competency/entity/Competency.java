package com.team.careerfit.competency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 표준 역량. 카드 태그·필터 칩·매칭 표에 이 이름이 그대로 나간다.
 *
 * <p><b>사용자가 만들지 않는다.</b> 사전은 관리자가 채우고, 앱은 시작 시 별칭과 함께 메모리에 올려 쓴다.
 * 그래서 변경 메서드가 없다 — 바꿀 일이 생기면 데이터를 고치고 다시 띄운다.
 */
@Entity
@Table(
        name = "competencies",
        uniqueConstraints = @UniqueConstraint(name = "uk_competencies_name", columnNames = "name"))
public class Competency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private CompetencyCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Competency() {
        // JPA 용
    }

    private Competency(String name, CompetencyCategory category) {
        this.name = name;
        this.category = category;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Competency of(String name, CompetencyCategory category) {
        return new Competency(name, category);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CompetencyCategory getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
