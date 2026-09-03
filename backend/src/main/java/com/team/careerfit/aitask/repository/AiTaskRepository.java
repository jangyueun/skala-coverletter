package com.team.careerfit.aitask.repository;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTaskRepository extends JpaRepository<AiTask, Long> {

    Optional<AiTask> findByIdempotencyKey(String idempotencyKey);

    Optional<AiTask> findFirstByTaskTypeAndJobPostingIdAndStatusInOrderByCreatedAtDesc(
            AiTaskType taskType, Long jobPostingId, Collection<AiTaskStatus> statuses);
}
