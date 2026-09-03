package com.team.careerfit.integration.ai.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.careerfit.integration.ai.config.AiProviderProperties;
import com.team.careerfit.integration.ai.dto.PromptVersions;
import com.team.careerfit.integration.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;

class AiProviderClientTest {

    /** AI 서버에 연결할 수 없으면 RestClientException 을 그대로 흘리지 않고 AiProviderException 으로 감싼다. */
    @Test
    void 연결에_실패하면_AiProviderException으로_감싼다() {
        AiProviderClient client = new AiProviderClient(
                new AiProviderProperties("http://localhost:59999", null));

        assertThatThrownBy(client::promptVersions)
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("/ai/prompts/versions");
    }

    /** promptVersions() 는 도메인이 아니라 AI 서버 값 그대로 통과시킨다 — 오타 없이 필드명이 맞는지만 본다. */
    @Test
    void 필드_이름은_명세와_같다() {
        PromptVersions versions = new PromptVersions("v2", "v1", "v1", "v1");
        org.assertj.core.api.Assertions.assertThat(versions.postingAnalysis()).isEqualTo("v2");
        org.assertj.core.api.Assertions.assertThat(versions.experienceIntake()).isEqualTo("v1");
    }
}
