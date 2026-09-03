package com.team.careerfit.aitask.dto;

/** AI 작업 생성 계열 API(인테이크·초안 등) 공통 응답 모양 {@code {"taskId": N}}. */
public record AiTaskCreatedResponse(Long taskId) {
}
