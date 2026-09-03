package com.team.careerfit.experience.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * experience_competencies 의 복합 키 (experience_id, competency_id).
 *
 * <p>대리 키를 두지 않았다. 한 경험에 같은 역량이 두 번 붙는 것을 PK 로 막는 게 목적이고,
 * 이 행을 밖에서 ID 로 가리킬 일이 없다.
 */
@Embeddable
public class ExperienceCompetencyId implements Serializable {

    @Column(name = "experience_id", nullable = false)
    private Long experienceId;

    @Column(name = "competency_id", nullable = false)
    private Long competencyId;

    protected ExperienceCompetencyId() {
        // JPA 용
    }

    public ExperienceCompetencyId(Long experienceId, Long competencyId) {
        this.experienceId = experienceId;
        this.competencyId = competencyId;
    }

    public Long getExperienceId() {
        return experienceId;
    }

    public Long getCompetencyId() {
        return competencyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExperienceCompetencyId that)) {
            return false;
        }
        return Objects.equals(experienceId, that.experienceId) && Objects.equals(competencyId, that.competencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(experienceId, competencyId);
    }
}
