package com.team.careerlab.aitask.entity;

/**
 * AI 작업 상태. PENDING → RUNNING → COMPLETED | FAILED 한 방향으로만 간다.
 *
 * <p>DB CHECK(ck_ai_task_timestamps)가 상태와 started_at · completed_at 의 짝을 강제한다.
 * 상태를 바꿀 때는 반드시 {@link AiTask} 의 전이 메서드를 써서 시각도 같이 맞춘다.
 */
public enum AiTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isInFlight() {
        return this == PENDING || this == RUNNING;
    }
}
