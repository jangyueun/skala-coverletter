package com.team.careerlab.aitask.worker;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskType;

/**
 * AI 작업 타입 하나를 실제로 처리하는 방법. 도메인마다(경험·자소서·공고·내부) 자기 타입의
 * 처리기를 빈으로 등록하면 {@link AiTaskWorker} 가 자동으로 주워 쓴다 — 워커는 타입별
 * 세부 사항(요청을 어떻게 만들고 결과를 어디에 반영하는지)을 모른다.
 */
public interface AiTaskHandler {

    AiTaskType type();

    /**
     * AI 제공자를 부르고, 도메인에 결과를 반영한다(예: 공고분석은 posting_competencies 교체).
     *
     * @throws com.team.careerlab.integration.ai.exception.AiProviderException AI 호출 실패 —
     *         {@link AiTaskWorker} 가 재시도 여부를 판단한다
     */
    Result handle(AiTask task);

    record Result(String model, String promptVersion, String resultPayload) {
    }
}
