package com.team.careerfit.integration.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.integration.ai.dto.PromptVersions;
import com.team.careerfit.integration.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;

class PromptVersionRegistryTest {

    private final AiProviderClient client = mock(AiProviderClient.class);
    private final PromptVersionRegistry registry = new PromptVersionRegistry(client);

    @Test
    void 한_번_받아_캐시하고_타입별_라벨을_만든다() {
        when(client.promptVersions()).thenReturn(new PromptVersions("v3", "v1", "v2", "v5"));

        assertThat(registry.of(AiTaskType.POSTING_ANALYSIS)).isEqualTo("posting_analysis/v3");
        assertThat(registry.of(AiTaskType.EXPERIENCE_INTAKE)).isEqualTo("experience_intake/v1");
        assertThat(registry.of(AiTaskType.MATCH)).isEqualTo("match/v2");
        assertThat(registry.of(AiTaskType.DRAFT)).isEqualTo("draft/v5");
        verify(client, times(1)).promptVersions();
    }

    @Test
    void AI_서버가_안_뜨면_기본값을_쓰고_다음에_다시_시도한다() {
        when(client.promptVersions())
                .thenThrow(new AiProviderException("down", null))
                .thenReturn(new PromptVersions("v9", "v1", "v1", "v1"));

        assertThat(registry.of(AiTaskType.POSTING_ANALYSIS)).isEqualTo("posting_analysis/v2");   // 기본값
        assertThat(registry.of(AiTaskType.POSTING_ANALYSIS)).isEqualTo("posting_analysis/v9");   // 다시 받았다
        verify(client, times(2)).promptVersions();
    }
}
