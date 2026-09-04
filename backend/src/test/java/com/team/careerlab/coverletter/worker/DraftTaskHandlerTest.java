package com.team.careerlab.coverletter.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.competency.entity.Competency;
import com.team.careerlab.competency.entity.CompetencyCategory;
import com.team.careerlab.experience.entity.Experience;
import com.team.careerlab.experience.entity.ExperienceCategory;
import com.team.careerlab.experience.repository.ExperienceRepository;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.dto.DraftRequest;
import com.team.careerlab.integration.ai.dto.DraftResponse;
import com.team.careerlab.job.entity.Company;
import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.job.entity.JobPostingQuestion;
import com.team.careerlab.job.repository.JobPostingQuestionRepository;
import com.team.careerlab.user.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class DraftTaskHandlerTest {

    private final AiProviderClient aiClient = mock(AiProviderClient.class);
    private final JobPostingQuestionRepository questions = mock(JobPostingQuestionRepository.class);
    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final DraftTaskHandler handler = new DraftTaskHandler(aiClient, questions, experiences, new JsonMapper());

    @Test
    void 문항_공고_근거_경험을_모아_보내고_초안을_result_로_남긴다() {
        User me = User.firstLogin("T", "U7", "지호", null, null);
        setId(me, 7L);
        User other = User.firstLogin("T", "U8", "남", null, null);
        setId(other, 8L);

        JobPosting posting = JobPosting.collect(Company.of("세움테크", "세움테크", null), "백엔드 엔지니어", "본문", null,
                Instant.now().plusSeconds(1));
        Competency api = Competency.of("API 설계·연동", CompetencyCategory.ROLE);
        setId(api, 3L);
        posting.replaceRequiredCompetencies(Map.of(api, new JobPosting.Requirement(new BigDecimal("0.9"), "REST")));
        JobPostingQuestion question = JobPostingQuestion.of(posting, 1, "지원 동기를 쓰시오", 700);
        when(questions.findById(31L)).thenReturn(Optional.of(question));

        Experience mine = Experience.register(me, "MSA 구축", ExperienceCategory.TEAM_PROJECT, null, null,
                null, "목표", "행동", "결과", null);
        setId(mine, 1L);
        Experience theirs = Experience.register(other, "남의 경험", ExperienceCategory.TEAM_PROJECT, null, null,
                "s", "t", "a", "r", null);
        setId(theirs, 4L);
        when(experiences.findAllById(List.of(4L, 1L))).thenReturn(List.of(mine, theirs));
        when(aiClient.draft(any())).thenReturn(new DraftResponse("초안 본문", 5, "draft/v1", "claude-opus-5"));

        AiTask task = AiTask.draft(7L, 31L, "key", "hash", "{\"questionId\":31,\"experienceIds\":[4,1]}");
        AiTaskHandler.Result result = handler.handle(task);

        ArgumentCaptor<DraftRequest> sent = ArgumentCaptor.forClass(DraftRequest.class);
        verify(aiClient).draft(sent.capture());
        DraftRequest request = sent.getValue();
        assertThat(request.question().promptText()).isEqualTo("지원 동기를 쓰시오");
        assertThat(request.question().lengthLimit()).isEqualTo(700);
        assertThat(request.posting().company()).isEqualTo("세움테크");
        assertThat(request.posting().content()).isEqualTo("본문");
        assertThat(request.posting().required()).hasSize(1);
        assertThat(request.posting().required().get(0).name()).isEqualTo("API 설계·연동");
        assertThat(request.posting().required().get(0).weight()).isEqualByComparingTo("0.9");
        assertThat(request.posting().required().get(0).evidenceLine()).isEqualTo("REST");
        // 남의 경험(4)은 빠지고, NULL 인 situation 은 빈 문자열로
        assertThat(request.experiences()).hasSize(1);
        assertThat(request.experiences().get(0).title()).isEqualTo("MSA 구축");
        assertThat(request.experiences().get(0).situation()).isEmpty();
        assertThat(request.experiences().get(0).result()).isEqualTo("결과");

        assertThat(result.model()).isEqualTo("claude-opus-5");
        assertThat(result.promptVersion()).isEqualTo("draft/v1");
        assertThat(result.resultPayload()).isEqualTo("{\"draft\":\"초안 본문\",\"charCount\":5}");
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
