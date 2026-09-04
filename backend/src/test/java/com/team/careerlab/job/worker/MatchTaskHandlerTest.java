package com.team.careerlab.job.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.competency.entity.CompetencyCategory;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.client.PromptVersionRegistry;
import com.team.careerlab.integration.ai.dto.MatchRequest;
import com.team.careerlab.integration.ai.dto.MatchResponse;
import com.team.careerlab.integration.ai.dto.MatchResponse.MatchRow;
import com.team.careerlab.job.entity.Company;
import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.job.repository.JobPostingRepository;
import com.team.careerlab.job.repository.PostingMatchQueryRepository;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.ExperienceInput;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.ExperienceView;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.Requirement;
import com.team.careerlab.job.service.MatchInputHash;
import com.team.careerlab.matching.entity.JobMatch;
import com.team.careerlab.matching.entity.MatchVerdict;
import com.team.careerlab.matching.repository.JobMatchRepository;
import com.team.careerlab.user.entity.User;
import com.team.careerlab.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class MatchTaskHandlerTest {

    private final AiProviderClient aiClient = mock(AiProviderClient.class);
    private final PostingMatchQueryRepository matches = mock(PostingMatchQueryRepository.class);
    private final JobMatchRepository jobMatches = mock(JobMatchRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final PromptVersionRegistry promptVersions = mock(PromptVersionRegistry.class);
    private final MatchTaskHandler handler = new MatchTaskHandler(
            aiClient, matches, jobMatches, users, postings, promptVersions, new JsonMapper());

    private final Requirement api = new Requirement(3L, "API 설계·연동", CompetencyCategory.ROLE, new BigDecimal("0.9"), "REST");
    private final Requirement k8s = new Requirement(13L, "Kubernetes", CompetencyCategory.TECH, new BigDecimal("0.6"), "EKS");
    private final List<ExperienceInput> inputs = List.of(
            new ExperienceInput(1L, 3L, new BigDecimal("0.8")),
            new ExperienceInput(2L, 3L, new BigDecimal("0.5")));

    @Test
    void 지금_DB_입력으로_AI를_부르고_job_matches_에_결과와_입력_해시를_쓴다() {
        when(matches.findRequirements(9L)).thenReturn(List.of(api, k8s));
        when(matches.findExperienceInputs(7L)).thenReturn(inputs);
        when(matches.findExperiences(any(), anyCollection())).thenReturn(List.of(
                new ExperienceView(1L, "MSA 구축", "결과 1"), new ExperienceView(2L, "", null)));
        when(aiClient.match(any())).thenReturn(new MatchResponse(new BigDecimal("0.6"), "HOLD", List.of(
                new MatchRow(3L, new BigDecimal("0.9"), BigDecimal.ONE, false, List.of(1L, 2L)),
                new MatchRow(13L, new BigDecimal("0.6"), BigDecimal.ZERO, true, List.of())),
                "match/v1", "rule-based"));
        when(jobMatches.findByUserIdAndJobPostingId(7L, 9L)).thenReturn(Optional.empty());
        User me = User.firstLogin("T", "U7", "지호", null, null);
        JobPosting posting = JobPosting.collect(Company.of("c", "c", null), "p", "x", null, Instant.now().plusSeconds(1));
        when(users.getReferenceById(7L)).thenReturn(me);
        when(postings.getReferenceById(9L)).thenReturn(posting);
        when(jobMatches.save(any(JobMatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiTask task = AiTask.match(7L, 9L, "key", "hash", "{\"jobPostingId\":9,\"triggeredByExperienceId\":1}");
        AiTaskHandler.Result result = handler.handle(task);

        ArgumentCaptor<MatchRequest> sent = ArgumentCaptor.forClass(MatchRequest.class);
        verify(aiClient).match(sent.capture());
        MatchRequest request = sent.getValue();
        assertThat(request.posting().id()).isEqualTo(9L);
        assertThat(request.posting().required()).extracting(MatchRequest.MatchRequirement::competencyId)
                .containsExactly(3L, 13L);
        assertThat(request.experiences()).hasSize(2);
        assertThat(request.experiences().get(1).title()).isEqualTo("경험 2");   // 제목 없는 경험도 계약(비어 있으면 안 됨)을 지킨다
        assertThat(request.experiences().get(1).result()).isEmpty();

        ArgumentCaptor<JobMatch> saved = ArgumentCaptor.forClass(JobMatch.class);
        verify(jobMatches).save(saved.capture());
        JobMatch match = saved.getValue();
        assertThat(match.getMatchScore()).isEqualByComparingTo("0.600");
        assertThat(match.getVerdict()).isEqualTo(MatchVerdict.HOLD);         // 점수에서 유도
        assertThat(match.getCoveredCount()).isEqualTo(1);
        assertThat(match.getInputHash()).isEqualTo(MatchInputHash.of(List.of(api, k8s), inputs));
        assertThat(match.getCoverage())
                .contains("\"competencyId\":3")
                .contains("\"isGap\":false")
                .contains("\"experiences\":[{\"id\":1,\"strength\":0.8},{\"id\":2,\"strength\":0.5}]")
                .contains("\"competencyId\":13");

        assertThat(result.model()).isEqualTo("rule-based");
        assertThat(result.promptVersion()).isEqualTo("match/v1");
        assertThat(result.resultPayload()).isEqualTo("{\"postingId\":9,\"score\":60,\"verdict\":\"HOLD\"}");
    }

    @Test
    void 이미_결과가_있으면_행을_덮어쓴다() {
        when(matches.findRequirements(9L)).thenReturn(List.of(api));
        when(matches.findExperienceInputs(7L)).thenReturn(inputs);
        when(matches.findExperiences(any(), anyCollection())).thenReturn(List.of());
        when(aiClient.match(any())).thenReturn(new MatchResponse(new BigDecimal("0.95"), "RECOMMEND", List.of(
                new MatchRow(3L, new BigDecimal("0.9"), BigDecimal.ONE, false, List.of(1L, 2L))), "match/v1", "rule-based"));
        User me = User.firstLogin("T", "U7", "지호", null, null);
        JobPosting posting = JobPosting.collect(Company.of("c", "c", null), "p", "x", null, Instant.now().plusSeconds(1));
        JobMatch existing = JobMatch.compute(me, posting, new BigDecimal("0.100"), 0, "[]", "old-hash");
        when(jobMatches.findByUserIdAndJobPostingId(7L, 9L)).thenReturn(Optional.of(existing));

        handler.handle(AiTask.match(7L, 9L, "key", "hash", "{}"));

        verify(jobMatches, never()).save(any());
        assertThat(existing.getMatchScore()).isEqualByComparingTo("0.950");
        assertThat(existing.getVerdict()).isEqualTo(MatchVerdict.RECOMMEND);
        assertThat(existing.getInputHash()).isEqualTo(MatchInputHash.of(List.of(api), inputs));
    }

    @Test
    void 요구_역량이_없는_공고는_AI를_부르지_않고_결과도_저장하지_않는다() {
        when(matches.findRequirements(9L)).thenReturn(List.of());
        when(promptVersions.of(AiTaskType.MATCH)).thenReturn("match/v1");

        AiTaskHandler.Result result = handler.handle(AiTask.match(7L, 9L, "key", "hash", "{}"));

        verify(aiClient, never()).match(any());
        verify(jobMatches, never()).save(any());
        assertThat(result.resultPayload()).isEqualTo("{\"postingId\":9,\"score\":null,\"verdict\":null}");
    }
}
