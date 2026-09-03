package com.team.careerfit.experience.repository;

import com.team.careerfit.experience.entity.Experience;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    long countByIdInAndUserId(Collection<Long> ids, Long userId);
}
