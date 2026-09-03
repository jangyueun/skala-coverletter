package com.team.careerfit.experience.repository;

import com.team.careerfit.experience.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
}
