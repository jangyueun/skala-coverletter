package com.team.careerfit.integration.ai.client;

import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.integration.ai.dto.PromptVersions;
import com.team.careerfit.integration.ai.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 서버의 프롬프트 버전({@code GET /ai/prompts/versions}). 작업 멱등 키에 들어간다(명세 §8).
 *
 * <p>왜 키에 넣나 — 프롬프트를 고쳤는데 키가 같으면 같은 입력의 작업이 옛 결과를 재사용한다. 버전이 키에 있으면
 * 프롬프트가 바뀐 뒤 첫 요청부터 새로 계산된다. 저장된 매칭 결과(job_matches.input_hash)에는 넣지 않는다 —
 * 프롬프트를 올릴 때마다 전부 stale 이 되면 안 되기 때문이다(JobMatch 주석).
 *
 * <p>처음 쓸 때 한 번 받아 캐시한다. AI 서버가 꺼져 있으면 서버가 지금 내는 값과 같은 기본값으로 대신하고 캐시하지
 * 않는다 — 다음 호출이 다시 시도한다. 기동 시점에 받지 않는 이유: AI 서버가 늦게 떠도 Spring 은 떠야 한다.
 */
@Component
public class PromptVersionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptVersionRegistry.class);

    /** ai/app/services/prompts.py(Claude 제공자)의 현재 값. 서버에 못 물어볼 때만 쓴다. */
    static final PromptVersions FALLBACK = new PromptVersions("v2", "v2", "v1", "v2");

    private final AiProviderClient client;
    private volatile PromptVersions cached;
    private volatile boolean warned;

    public PromptVersionRegistry(AiProviderClient client) {
        this.client = client;
    }

    /** 예: {@code draft/v1}. AI 서버 응답의 promptVersion 과 같은 꼴이다. */
    public String of(AiTaskType type) {
        PromptVersions versions = current();
        return switch (type) {
            case POSTING_ANALYSIS -> "posting_analysis/" + versions.postingAnalysis();
            case EXPERIENCE_INTAKE -> "experience_intake/" + versions.experienceIntake();
            case MATCH -> "match/" + versions.match();
            case DRAFT -> "draft/" + versions.draft();
        };
    }

    public PromptVersions current() {
        PromptVersions versions = cached;
        if (versions != null) {
            return versions;
        }
        try {
            versions = client.promptVersions();
            if (versions == null) {
                throw new AiProviderException("프롬프트 버전 응답이 비었습니다", null);
            }
            cached = versions;
            warned = false;
            return versions;
        } catch (AiProviderException e) {
            if (!warned) {
                log.warn("AI 서버에서 프롬프트 버전을 못 받아 기본값을 씁니다: {}", e.getMessage());
                warned = true;
            }
            return FALLBACK;
        }
    }

    /** 테스트·운영 중 강제 재조회용. */
    public void refresh() {
        cached = null;
    }
}
