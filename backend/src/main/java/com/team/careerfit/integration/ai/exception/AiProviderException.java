package com.team.careerfit.integration.ai.exception;

/**
 * AI 제공자 호출 실패(422·503·타임아웃·연결 실패 등을 뭉뚱그린다).
 *
 * <p>API 응답으로 바로 나가지 않는다 — 이 작업을 만든 도메인 서비스가 잡아서
 * {@code ai_tasks.attempts} 에 기록하고 재시도하거나 FAILED 로 남기는 데 쓴다.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
