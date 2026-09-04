package com.team.careerfit.aitask.dto;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /api/ai-tasks/{taskId}} (docs/api-spec-v6.md §6). 프론트(api/real/ai.js)는 status 가 COMPLETED 면
 * result 를, FAILED 면 error 를 읽는다.
 *
 * <p>result 는 워커가 저장한 result_payload(jsonb)를 그대로 낸다 — 타입별 모양은 처리기가 정한다:
 * DRAFT {draft, charCount} · EXPERIENCE_INTAKE {candidates[]} · MATCH {postingId, score, verdict} ·
 * POSTING_ANALYSIS {postingId, requiredCount}.
 */
public record AiTaskResponse(
        Long taskId,
        AiTaskType type,
        AiTaskStatus status,
        Instant createdAt,
        Instant completedAt,
        int attempts,
        String model,
        String promptVersion,
        JsonNode result,
        Error error) {

    public record Error(String code, String message) {
    }

    public static AiTaskResponse from(AiTask task, ObjectMapper objectMapper) {
        return new AiTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                attemptCount(task, objectMapper),
                task.getModel(),
                task.getPromptVersion(),
                task.getResultPayload() == null ? null : objectMapper.readTree(task.getResultPayload()),
                task.getStatus() == AiTaskStatus.FAILED ? new Error(task.getErrorCode(), task.getErrorMessage()) : null);
    }

    /** attempts 는 시도 기록 배열(jsonb)이다. 성공한 시도는 기록에 안 남으므로 재시도 수 + 1 이 실제 시도 수다. */
    private static int attemptCount(AiTask task, ObjectMapper objectMapper) {
        if (task.getStatus() == AiTaskStatus.PENDING) {
            return 0;
        }
        return task.getRetryCount() + (task.getStatus() == AiTaskStatus.FAILED ? 0 : 1);
    }
}
