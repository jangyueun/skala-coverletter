package com.team.careerlab.internal.worker;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.competency.entity.Competency;
import com.team.careerlab.competency.repository.CompetencyRepository;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.dto.PostingAnalysisRequest;
import com.team.careerlab.integration.ai.dto.PostingAnalysisResponse;
import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.job.repository.JobPostingRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * POSTING_ANALYSIS 작업을 실제로 처리한다. {@code request_payload} 가 이미
 * {@link PostingAnalysisRequest} 그대로의 완성된 스냅샷이라(PostingAnalysisService 가 생성 시점에
 * 다 채워 둔다) 다른 타입과 달리 추가로 데이터를 긁어모을 필요가 없다 — 그대로 역직렬화해서 보낸다.
 *
 * <p>완료되면 {@code posting_competencies} 를 통째로 교체하고 {@code analyzed_at} 을 찍는다
 * ({@link JobPosting#replaceRequiredCompetencies}). 사전 밖 competencyId 는 조용히 버린다 —
 * AI 서버가 사전에 없는 걸 지어낼 수 없다는 계약을 서버 쪽에서도 다시 한 번 지킨다.
 */
@Component
public class PostingAnalysisTaskHandler implements AiTaskHandler {

    private final AiProviderClient aiClient;
    private final JobPostingRepository jobPostings;
    private final CompetencyRepository competencies;
    private final ObjectMapper objectMapper;

    public PostingAnalysisTaskHandler(AiProviderClient aiClient, JobPostingRepository jobPostings,
            CompetencyRepository competencies, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.jobPostings = jobPostings;
        this.competencies = competencies;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTaskType type() {
        return AiTaskType.POSTING_ANALYSIS;
    }

    @Override
    public Result handle(AiTask task) {
        PostingAnalysisRequest request = objectMapper.readValue(task.getRequestPayload(), PostingAnalysisRequest.class);
        PostingAnalysisResponse response = aiClient.postingAnalysis(request);

        JobPosting posting = jobPostings.findById(task.getJobPostingId())
                .orElseThrow(() -> new IllegalStateException("공고를 찾을 수 없습니다: " + task.getJobPostingId()));

        Map<Competency, JobPosting.Requirement> requirements = new HashMap<>();
        for (PostingAnalysisResponse.RequiredCompetency required : response.required()) {
            competencies.findById(required.competencyId())
                    .ifPresent(competency -> requirements.put(competency,
                            new JobPosting.Requirement(required.weight(), required.evidence())));
        }
        posting.replaceRequiredCompetencies(requirements);

        String resultPayload = objectMapper.writeValueAsString(
                new AnalysisResult(task.getJobPostingId(), requirements.size()));
        return new Result(response.model(), response.promptVersion(), resultPayload);
    }

    private record AnalysisResult(Long postingId, int requiredCount) {
    }
}
