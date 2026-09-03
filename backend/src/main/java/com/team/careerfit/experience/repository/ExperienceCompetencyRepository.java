package com.team.careerfit.experience.repository;

import com.team.careerfit.experience.entity.ExperienceCompetency;
import com.team.careerfit.experience.entity.ExperienceCompetencyId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceCompetencyRepository extends JpaRepository<ExperienceCompetency, ExperienceCompetencyId> {

    /** 역량 이름까지 한 번에 당겨온다 — 목록에서 경험마다 역량을 다시 조회하면 N+1 이 된다. */
    @Query("""
            SELECT ec FROM ExperienceCompetency ec
            JOIN FETCH ec.competency
            WHERE ec.experience.id IN :experienceIds
            """)
    List<ExperienceCompetency> findByExperienceIdInFetchCompetency(@Param("experienceIds") List<Long> experienceIds);
}
