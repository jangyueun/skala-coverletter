package com.team.careerlab.coverletter.service;

import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.coverletter.exception.CoverLetterException;
import com.team.careerlab.experience.service.ExperienceService;
import com.team.careerlab.job.repository.JobPostingQuestionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자소서 초안 요청. <b>초안 자체는 저장하지 않는다</b> — 결과는 {@code GET /api/ai-tasks/{taskId}} 에서
 * {@code result.draft} 로 조회한다(그 폴링 API는 이번 범위 밖이다).
 *
 * <p>이번 범위는 DRAFT 작업을 PENDING 으로 만들고 taskId 를 돌려주는 것까지다. AI 계약
 * ({@code POST /ai/draft})이 요구하는 전체 페이로드(문항 텍스트, 공고 요구 역량, 경험 STAR 전문)는
 * MockAiClient·워커를 붙일 때 채운다 — 지금은 questionId·experienceIds 만 담은 최소 스냅샷이다.
 */
@Service
public class CoverLetterDraftService {

    private final JobPostingQuestionRepository questions;
    private final ExperienceService experiences;
    private final AiTaskService aiTasks;

    public CoverLetterDraftService(JobPostingQuestionRepository questions, ExperienceService experiences,
            AiTaskService aiTasks) {
        this.questions = questions;
        this.experiences = experiences;
        this.aiTasks = aiTasks;
    }

    /**
     * @throws CoverLetterException 문항이 없으면 {@code QUESTION_NOT_FOUND}, 남의 경험이 섞여 있으면 {@code FORBIDDEN}
     * @throws com.team.careerlab.aitask.exception.AiTaskException 다른 근거 경험 선택으로 이미
     *         진행 중인 초안이 있으면 {@code DRAFT_ALREADY_RUNNING}
     */
    @Transactional
    public Result requestDraft(Long userId, Long questionId, List<Long> experienceIds) {
        if (!questions.existsById(questionId)) {
            throw CoverLetterException.questionNotFound();
        }
        if (!experiences.allOwnedBy(userId, experienceIds)) {
            throw CoverLetterException.forbidden();
        }

        String payload = "{\"questionId\":" + questionId + ",\"experienceIds\":" + experienceIds + "}";
        AiTaskService.Reservation reservation = aiTasks.reserveDraftTask(userId, questionId, payload);

        return new Result(reservation.taskId(), reservation.created());
    }

    public record Result(Long taskId, boolean created) {
    }
}
