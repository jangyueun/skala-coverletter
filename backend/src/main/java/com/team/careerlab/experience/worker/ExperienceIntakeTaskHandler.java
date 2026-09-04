package com.team.careerlab.experience.worker;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.competency.service.CompetencyService;
import com.team.careerlab.experience.repository.ExperienceRepository;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.dto.AiCompetency;
import com.team.careerlab.integration.ai.dto.ExperienceIntakeRequest;
import com.team.careerlab.integration.ai.dto.ExperienceIntakeRequest.ExistingExperience;
import com.team.careerlab.integration.ai.dto.ExperienceIntakeResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * EXPERIENCE_INTAKE 작업 — 링크·첨부 URL 에 이미 등록된 경험과 역량 사전을 붙여 AI 서버 {@code POST /ai/experience-intake}
 * 를 부른다. AI 서버가 자료를 직접 읽고(web_fetch) 경험 후보를 돌려준다.
 *
 * <p>request_payload 는 {@code ExperienceIntakeService} 가 만든 {links, fileUrls} 다. 이미 등록된 경험을 같이 보내는 이유 —
 * 같은 프로젝트를 또 후보로 내지 않게 하고, 같은 제목이면 duplicateOfExperienceId 로 표시해 프론트가 잠근다.
 * 결과(후보 목록)는 폴링 응답의 result.candidates 로 나가고, 사용자가 고쳐서 등록할 때 intakeTaskId 로 출처가 남는다.
 */
@Component
public class ExperienceIntakeTaskHandler implements AiTaskHandler {

    private final AiProviderClient aiClient;
    private final ExperienceRepository experiences;
    private final CompetencyService competencies;
    private final ObjectMapper objectMapper;

    public ExperienceIntakeTaskHandler(AiProviderClient aiClient, ExperienceRepository experiences,
            CompetencyService competencies, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.experiences = experiences;
        this.competencies = competencies;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTaskType type() {
        return AiTaskType.EXPERIENCE_INTAKE;
    }

    @Override
    public Result handle(AiTask task) {
        Payload payload = objectMapper.readValue(task.getRequestPayload(), Payload.class);

        List<ExistingExperience> existing = experiences.findByUserIdOrderByStartDateDesc(task.getUserId()).stream()
                .map(experience -> new ExistingExperience(experience.getId(), experience.getTitle(),
                        experience.getCategory().name(), experience.getStartDate(), experience.getEndDate()))
                .toList();
        List<AiCompetency> dictionary = competencies.findAll(null).stream()
                .map(competency -> new AiCompetency(competency.id(), competency.name(),
                        competency.category().name(), competency.aliases()))
                .toList();

        ExperienceIntakeRequest request = new ExperienceIntakeRequest(
                payload.links() == null ? List.of() : payload.links(),
                payload.fileUrls() == null ? List.of() : payload.fileUrls(),
                existing,
                dictionary);
        ExperienceIntakeResponse response = aiClient.experienceIntake(request);

        String resultPayload = objectMapper.writeValueAsString(new IntakeResult(response.candidates()));
        return new Result(response.model(), response.promptVersion(), resultPayload);
    }

    record Payload(List<String> links, List<String> fileUrls) {
    }

    /** 폴링 응답의 result — 명세 §6: EXPERIENCE_INTAKE {candidates[]}. */
    record IntakeResult(List<ExperienceIntakeResponse.Candidate> candidates) {
    }
}
