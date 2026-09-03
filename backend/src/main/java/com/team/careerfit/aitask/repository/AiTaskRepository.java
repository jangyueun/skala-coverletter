package com.team.careerfit.aitask.repository;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTaskRepository extends JpaRepository<AiTask, Long> {

    Optional<AiTask> findByIdempotencyKey(String idempotencyKey);

    /** 부분 UNIQUE(uq_ai_task_intake_inflight)가 보장하는 "사용자당 진행 중 인테이크 1개"를 그대로 조회한다. */
    Optional<AiTask> findFirstByTaskTypeAndUserIdAndStatusIn(AiTaskType taskType, Long userId,
            List<AiTaskStatus> statuses);
}
