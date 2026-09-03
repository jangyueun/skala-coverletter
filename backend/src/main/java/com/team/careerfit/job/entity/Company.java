package com.team.careerfit.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 채용 기업. 공고 카드의 회사 이름과 상세의 "같은 기업 공고" 묶음이 이 행을 가리킨다.
 *
 * <p><b>중복 제거 기준은 {@code normalizedName} 이다.</b> 수집 출처마다 "(주)세움테크" · "세움테크 주식회사"
 * 처럼 표기가 달라서, 화면용 이름과 비교용 이름을 따로 둔다.
 */
@Entity
@Table(
        name = "companies",
        uniqueConstraints = @UniqueConstraint(name = "uk_companies_normalized_name", columnNames = "normalized_name"))
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "career_url", length = 1000)
    private String careerUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
        // JPA 용
    }

    private Company(String name, String normalizedName, String careerUrl) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.careerUrl = careerUrl;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Company of(String name, String normalizedName, String careerUrl) {
        return new Company(name, normalizedName, careerUrl);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getCareerUrl() {
        return careerUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
