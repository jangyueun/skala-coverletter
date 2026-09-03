package com.team.careerfit.experience.service;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.service.AiTaskService;
import com.team.careerfit.competency.entity.Competency;
import com.team.careerfit.competency.repository.CompetencyRepository;
import com.team.careerfit.experience.dto.ExperienceCreateRequest;
import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.dto.ExperienceUpdateRequest;
import com.team.careerfit.experience.entity.Experience;
import com.team.careerfit.experience.entity.ExperienceCompetency;
import com.team.careerfit.experience.exception.ExperienceException;
import com.team.careerfit.experience.repository.ExperienceCompetencyRepository;
import com.team.careerfit.experience.repository.ExperienceRepository;
import com.team.careerfit.job.service.JobPostingService;
import com.team.careerfit.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceService {

    private final ExperienceRepository experiences;
    private final ExperienceCompetencyRepository experienceCompetencies;
    private final CompetencyRepository competencies;
    private final JobPostingService jobPostings;
    private final AiTaskService aiTasks;

    public ExperienceService(ExperienceRepository experiences, ExperienceCompetencyRepository experienceCompetencies,
            CompetencyRepository competencies, JobPostingService jobPostings, AiTaskService aiTasks) {
        this.experiences = experiences;
        this.experienceCompetencies = experienceCompetencies;
        this.competencies = competencies;
        this.jobPostings = jobPostings;
        this.aiTasks = aiTasks;
    }

    /** 자소서 답변·초안의 근거 경험이 전부 이 사용자 소유인지 — 다른 도메인은 repository가 아니라 이 서비스로만 확인한다. */
    @Transactional(readOnly = true)
    public boolean allOwnedBy(Long userId, List<Long> experienceIds) {
        Set<Long> distinctIds = new HashSet<>(experienceIds);
        if (distinctIds.isEmpty()) {
            return true;
        }
        return experiences.countByIdInAndUserId(distinctIds, userId) == distinctIds.size();
    }

    /** competencyId 가 없으면 전체, 있으면 그 역량이 붙은 경험만 돌려준다. */
    @Transactional(readOnly = true)
    public List<ExperienceResponse> list(Long userId, Long competencyId) {
        List<Experience> found = competencyId == null
                ? experiences.findByUserIdOrderByStartDateDesc(userId)
                : experiences.findByUserIdAndCompetencyId(userId, competencyId);

        if (found.isEmpty()) {
            return List.of();
        }

        List<Long> experienceIds = found.stream().map(Experience::getId).toList();

        Map<Long, List<ExperienceCompetency>> competenciesByExperience = experienceCompetencies
                .findByExperienceIdInFetchCompetency(experienceIds).stream()
                .collect(Collectors.groupingBy(ec -> ec.getExperience().getId()));

        Map<Long, Long> usedCounts = experiences.countUsedInQuestions(userId).stream()
                .collect(Collectors.toMap(ExperienceRepository.UsedCount::getExperienceId,
                        ExperienceRepository.UsedCount::getUsedCount));

        return found.stream()
                .map(e -> ExperienceResponse.of(e,
                        competenciesByExperience.getOrDefault(e.getId(), List.of()),
                        usedCounts.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    /**
     * 경험을 새로 등록하고, 이 경험 때문에 매칭 결과가 바뀔 수 있는 활성 공고마다 MATCH 작업을 만든다.
     *
     * @throws ExperienceException 제목·결과가 비었거나, 역량이 없거나, 존재하지 않는 역량이거나,
     *         종료일이 시작일보다 빠르면 {@code VALIDATION_FAILED}
     */
    @Transactional
    public ExperienceSaveResponse register(User user, ExperienceCreateRequest request) {
        validatePeriod(request.startDate(), request.endDate());
        Map<Competency, BigDecimal> strengths = resolveCompetencies(request.competencies().stream()
                .collect(Collectors.toMap(ExperienceCreateRequest.CompetencyStrength::competencyId,
                        ExperienceCreateRequest.CompetencyStrength::strength, (a, b) -> b, LinkedHashMap::new)));

        Experience experience = Experience.register(user, request.title(), request.category(), request.startDate(),
                request.endDate(), request.situation(), request.task(), request.action(), request.result(),
                request.intakeTaskId());
        experience = experiences.save(experience);
        experience.replaceCompetencies(strengths);

        ExperienceResponse response = ExperienceResponse.of(experience, experience.getCompetencies(), 0L);
        ExperienceSaveResponse.Reassess reassess = reassessActivePostings(user.getId(), experience.getId());

        return new ExperienceSaveResponse(response, reassess);
    }

    /**
     * 경험을 통째로 다시 쓰고, 이 경험 때문에 매칭 결과가 바뀔 수 있는 활성 공고마다 MATCH 작업을 다시 만든다.
     *
     * @throws ExperienceException 대상이 없으면 {@code EXPERIENCE_NOT_FOUND}, 남의 경험이면 {@code FORBIDDEN},
     *         제목·결과가 비었거나 역량이 없거나 존재하지 않는 역량이거나 종료일이 시작일보다 빠르면 {@code VALIDATION_FAILED}
     */
    @Transactional
    public ExperienceSaveResponse update(User user, Long experienceId, ExperienceUpdateRequest request) {
        Experience experience = experiences.findById(experienceId).orElseThrow(ExperienceException::notFound);
        if (!experience.isOwnedBy(user.getId())) {
            throw ExperienceException.forbidden();
        }

        validatePeriod(request.startDate(), request.endDate());
        Map<Competency, BigDecimal> strengths = resolveCompetencies(request.competencies().stream()
                .collect(Collectors.toMap(ExperienceUpdateRequest.CompetencyStrength::competencyId,
                        ExperienceUpdateRequest.CompetencyStrength::strength, (a, b) -> b, LinkedHashMap::new)));

        experience.update(request.title(), request.category(), request.startDate(), request.endDate(),
                request.situation(), request.task(), request.action(), request.result());
        experience.replaceCompetencies(strengths);

        ExperienceResponse response = ExperienceResponse.of(experience, experience.getCompetencies(), 0L);
        ExperienceSaveResponse.Reassess reassess = reassessActivePostings(user.getId(), experience.getId());

        return new ExperienceSaveResponse(response, reassess);
    }

    private ExperienceSaveResponse.Reassess reassessActivePostings(Long userId, Long experienceId) {
        List<Long> activePostingIds = jobPostings.findActivePostingIds();
        List<Long> taskIds = activePostingIds.stream()
                .map(postingId -> aiTasks.createMatchTask(userId, postingId, matchTaskPayload(experienceId, postingId)))
                .map(AiTask::getId)
                .toList();
        return new ExperienceSaveResponse.Reassess(taskIds.size(), taskIds);
    }

    /**
     * 지금은 최소 스냅샷만 담는다. {@code POST /ai/match} 계약 그대로의 페이로드(공고 요구 역량,
     * 사용자 전체 경험)는 MockAiClient·워커를 붙일 때 채운다 — 이번 범위는 작업 생성까지다.
     */
    private String matchTaskPayload(Long experienceId, Long jobPostingId) {
        return "{\"jobPostingId\":" + jobPostingId + ",\"triggeredByExperienceId\":" + experienceId + "}";
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ExperienceException.validationFailed("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    /** 등록·수정 요청 DTO는 서로 다른 record(ExperienceCreateRequest.CompetencyStrength 등)라 호출부에서 먼저 맵으로 편다. */
    private Map<Competency, BigDecimal> resolveCompetencies(Map<Long, BigDecimal> strengthByCompetencyId) {
        Map<Long, Competency> byId = competencies.findAllById(List.copyOf(strengthByCompetencyId.keySet())).stream()
                .collect(Collectors.toMap(Competency::getId, competency -> competency));

        if (byId.size() != strengthByCompetencyId.size()) {
            throw ExperienceException.validationFailed("존재하지 않는 역량이 포함되어 있습니다.");
        }

        Map<Competency, BigDecimal> strengths = new LinkedHashMap<>();
        strengthByCompetencyId.forEach((competencyId, strength) -> strengths.put(byId.get(competencyId), strength));
        return strengths;
    }
}
