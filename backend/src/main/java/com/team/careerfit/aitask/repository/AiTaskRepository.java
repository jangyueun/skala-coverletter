package com.team.careerfit.aitask.repository;

import com.team.careerfit.aitask.entity.AiTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTaskRepository extends JpaRepository<AiTask, Long> {

    Optional<AiTask> findByIdempotencyKey(String idempotencyKey);
}
