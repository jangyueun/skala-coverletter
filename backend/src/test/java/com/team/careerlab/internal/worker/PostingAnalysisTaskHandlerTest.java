package com.team.careerlab.internal.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.competency.entity.Competency;
import com.team.careerlab.competency.entity.CompetencyCategory;
import com.team.careerlab.competency.repository.CompetencyRepository;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.dto.PostingAnalysisResponse;
import com.team.careerlab.integration.ai.dto.PostingAnalysisResponse.RequiredCompetency;
import com.team.careerlab.job.entity.Company;
import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.job.repository.JobPostingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PostingAnalysisTaskHandlerTest {

    private final AiProviderClient aiClient = mock(AiProviderClient.class);
    private final JobPostingRepository jobPostings = mock(JobPostingRepository.class);
    private final CompetencyRepository competencies = mock(CompetencyRepository.class);
    private final PostingAnalysisTaskHandler handler = new PostingAnalysisTaskHandler(
            aiClient, jobPostings, competencies, new JsonMapper());

    @Test
    void 응답의_요구_역량으로_공고를_갱신한다() {
        AiTask task = AiTask.postingAnalysis(9L, "key", "hash",
                "{\"postingId\":9,\"content\":\"REST API 설계\",\"competencies\":[]}");

        Company company = Company.of("세움테크", "세움테크", null);
        JobPosting posting = JobPosting.collect(company, "백엔드", "REST API 설계", null, Instant.now().plusSeconds(1));
        Competency competency = Competency.of("API 설계·연동", CompetencyCategory.ROLE);
        setId(competency, 3L);
        when(jobPostings.findById(9L)).thenReturn(Optional.of(posting));
        when(competencies.findById(3L)).thenReturn(Optional.of(competency));
        when(aiClient.postingAnalysis(any())).thenReturn(new PostingAnalysisResponse(
                List.of(new RequiredCompetency(3L, new BigDecimal("0.9"), "REST API 설계")),
                "posting_analysis/v2", "mock-ai"));

        AiTaskHandler.Result result = handler.handle(task);

        assertThat(result.model()).isEqualTo("mock-ai");
        assertThat(result.promptVersion()).isEqualTo("posting_analysis/v2");
        assertThat(posting.isAnalyzed()).isTrue();
        assertThat(posting.getRequiredCompetencies()).hasSize(1);
        assertThat(posting.getRequiredCompetencies().get(0).getCompetency().getName()).isEqualTo("API 설계·연동");
    }

    @Test
    void 사전에_없는_역량_ID는_조용히_버린다() {
        AiTask task = AiTask.postingAnalysis(9L, "key", "hash",
                "{\"postingId\":9,\"content\":\"c\",\"competencies\":[]}");
        Company company = Company.of("세움테크", "세움테크", null);
        JobPosting posting = JobPosting.collect(company, "백엔드", "c", null, Instant.now().plusSeconds(1));
        when(jobPostings.findById(9L)).thenReturn(Optional.of(posting));
        when(competencies.findById(999L)).thenReturn(Optional.empty());
        when(aiClient.postingAnalysis(any())).thenReturn(new PostingAnalysisResponse(
                List.of(new RequiredCompetency(999L, new BigDecimal("0.9"), "근거")),
                "posting_analysis/v2", "mock-ai"));

        handler.handle(task);

        assertThat(posting.getRequiredCompetencies()).isEmpty();
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
