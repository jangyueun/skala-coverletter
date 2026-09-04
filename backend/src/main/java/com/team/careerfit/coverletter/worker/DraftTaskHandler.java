package com.team.careerfit.coverletter.worker;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.worker.AiTaskHandler;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.integration.ai.client.AiProviderClient;
import com.team.careerfit.integration.ai.dto.DraftRequest;
import com.team.careerfit.integration.ai.dto.DraftRequest.DraftExperience;
import com.team.careerfit.integration.ai.dto.DraftRequest.DraftPosting;
import com.team.careerfit.integration.ai.dto.DraftRequest.DraftQuestion;
import com.team.careerfit.integration.ai.dto.DraftRequest.DraftRequirement;
import com.team.careerfit.integration.ai.dto.DraftResponse;
import com.team.careerfit.job.entity.JobPosting;
import com.team.careerfit.job.entity.JobPostingQuestion;
import com.team.careerfit.job.repository.JobPostingQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * DRAFT 작업 — 문항 · 공고 · 근거 경험(STAR 전문)을 모아 AI 서버 {@code POST /ai/draft} 를 부른다.
 *
 * <p>request_payload 에는 questionId · experienceIds 만 있다({@code CoverLetterDraftService}). 문항 텍스트와 경험 본문은
 * 여기서 다시 읽는다 — 요청 시점과 처리 시점 사이에 경험을 고쳤다면 고친 쪽이 맞다.
 * 초안은 저장하지 않는다. 결과는 폴링 응답의 result {draft, charCount} 로만 나가고, 사용자가 저장을 누를 때
 * draftTaskId 로 출처가 남는다.
 */
@Component
public class DraftTaskHandler implements AiTaskHandler {

    private final AiProviderClient aiClient;
    private final JobPostingQuestionRepository questions;
    private final ExperienceRepository experiences;
    private final ObjectMapper objectMapper;

    public DraftTaskHandler(AiProviderClient aiClient, JobPostingQuestionRepository questions,
            ExperienceRepository experiences, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.questions = questions;
        this.experiences = experiences;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTaskType type() {
        return AiTaskType.DRAFT;
    }

    @Override
    public Result handle(AiTask task) {
        Payload payload = objectMapper.readValue(task.getRequestPayload(), Payload.class);
        JobPostingQuestion question = questions.findById(payload.questionId())
                .orElseThrow(() -> new IllegalStateException("문항을 찾을 수 없습니다: " + payload.questionId()));
        JobPosting posting = question.getJobPosting();

        List<DraftRequirement> required = posting.getRequiredCompetencies().stream()
                .map(pc -> new DraftRequirement(pc.getCompetency().getName(), pc.getWeight(), nz(pc.getEvidenceLine())))
                .toList();

        // 요청한 순서를 지킨다 — 사용자가 고른 순서가 초안에서 다뤄지는 순서다. 남의 경험은 생성 시점에 걸렀지만 한 번 더.
        Map<Long, Experience> byId = experiences.findAllById(payload.experienceIds()).stream()
                .filter(experience -> experience.getUser().getId().equals(task.getUserId()))
                .collect(Collectors.toMap(Experience::getId, Function.identity()));
        List<DraftExperience> draftExperiences = payload.experienceIds().stream()
                .map(byId::get)
                .filter(experience -> experience != null)
                .map(experience -> new DraftExperience(
                        experience.getTitle(),
                        nz(experience.getSituation()),
                        nz(experience.getTask()),
                        nz(experience.getAction()),
                        nz(experience.getResult())))
                .toList();

        DraftRequest request = new DraftRequest(
                new DraftQuestion(question.getPromptText(), question.getLengthLimit()),
                new DraftPosting(posting.getCompany().getName(), posting.getPosition(), nz(posting.getContent()), required),
                draftExperiences);
        DraftResponse response = aiClient.draft(request);

        String resultPayload = objectMapper.writeValueAsString(new DraftResult(response.draft(), response.charCount()));
        return new Result(response.model(), response.promptVersion(), resultPayload);
    }

    /** AI 계약의 STAR 칸은 문자열이다(null 불가). DB 의 NULL 은 빈 칸으로 보낸다. */
    private static String nz(String value) {
        return value == null ? "" : value;
    }

    record Payload(Long questionId, List<Long> experienceIds) {
    }

    /** 폴링 응답의 result — 명세 §6: DRAFT {draft, charCount}. */
    record DraftResult(String draft, int charCount) {
    }
}
