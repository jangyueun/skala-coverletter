package com.team.careerlab.aitask.dto;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/ai-tasks} (docs/api-spec-v6.md §6) — 내 작업 현황. 결과 본문은 싣지 않는다, 단건 조회로 받는다. */
public record AiTaskListResponse(Counts counts, List<Item> items) {

    public record Counts(long pending, long running, long completed, long failed) {
    }

    public record Item(
            Long taskId,
            AiTaskType type,
            AiTaskStatus status,
            Long postingId,
            Long questionId,
            Instant createdAt) {

        public static Item from(AiTask task) {
            return new Item(task.getId(), task.getTaskType(), task.getStatus(), task.getJobPostingId(),
                    task.getQuestionId(), task.getCreatedAt());
        }
    }

    public static AiTaskListResponse of(List<AiTask> tasks) {
        Counts counts = new Counts(
                count(tasks, AiTaskStatus.PENDING),
                count(tasks, AiTaskStatus.RUNNING),
                count(tasks, AiTaskStatus.COMPLETED),
                count(tasks, AiTaskStatus.FAILED));
        return new AiTaskListResponse(counts, tasks.stream().map(Item::from).toList());
    }

    private static long count(List<AiTask> tasks, AiTaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }
}
