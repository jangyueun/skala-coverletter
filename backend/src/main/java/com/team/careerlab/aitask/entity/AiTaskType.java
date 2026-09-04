package com.team.careerlab.aitask.entity;

/** AI 작업 4종. 어떤 대상 ID 가 필수인지는 DB CHECK(ck_ai_task_targets)와 {@link AiTask} 팩토리가 함께 지킨다. */
public enum AiTaskType {
    /** 공고 원문 → 요구 역량·가중치·근거. 대상: job_posting_id */
    POSTING_ANALYSIS,
    /** 링크·첨부파일 → 경험 후보. 대상: user_id */
    EXPERIENCE_INTAKE,
    /** 사용자 경험 × 공고 요구 역량 → 커버리지·판정. 대상: user_id + job_posting_id */
    MATCH,
    /** 문항 + 선택 경험 → 자소서 초안. 대상: user_id + question_id */
    DRAFT
}
