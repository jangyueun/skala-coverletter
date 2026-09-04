package com.team.careerlab.competency.repository;

import com.team.careerlab.competency.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {
}
