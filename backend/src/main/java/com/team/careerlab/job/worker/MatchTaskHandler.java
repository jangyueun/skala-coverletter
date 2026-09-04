package com.team.careerlab.job.worker;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.worker.AiTaskHandler;
import com.team.careerlab.integration.ai.client.AiProviderClient;
import com.team.careerlab.integration.ai.client.PromptVersionRegistry;
import com.team.careerlab.integration.ai.dto.MatchRequest;
import com.team.careerlab.integration.ai.dto.MatchRequest.ExperienceStrength;
import com.team.careerlab.integration.ai.dto.MatchRequest.MatchExperience;
import com.team.careerlab.integration.ai.dto.MatchRequest.MatchPosting;
import com.team.careerlab.integration.ai.dto.MatchRequest.MatchRequirement;
import com.team.careerlab.integration.ai.dto.MatchResponse;
import com.team.careerlab.job.repository.JobPostingRepository;
import com.team.careerlab.job.repository.PostingMatchQueryRepository;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.ExperienceInput;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.ExperienceView;
import com.team.careerlab.job.repository.PostingMatchQueryRepository.Requirement;
import com.team.careerlab.job.service.MatchInputHash;
import com.team.careerlab.matching.entity.JobMatch;
import com.team.careerlab.matching.repository.JobMatchRepository;
import com.team.careerlab.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * MATCH 작업 — 사용자 × 공고의 매칭을 계산해 {@code job_matches} 에 쓴다.
 *
 * <p><b>request_payload 는 안 본다.</b> 만드는 쪽이 둘이라(경험 저장 → 활성 공고 전부, 매칭 탭 → 그 공고) 스냅샷 모양이
 * 다르고, 어차피 "지금" 의 요구 역량과 경험으로 계산해야 한다. 그래서 DB 에서 다시 읽고, 그 입력의 해시를
 * {@link MatchInputHash} 로 계산해 결과와 같이 저장한다 — 읽는 쪽({@code PostingMatchService})이 같은 식으로 stale 여부를 본다.
 *
 * <p>AI 서버의 match 는 결정론 공식이다(ai/app/services/matching.py). 판정(verdict)은 AI 응답을 믿지 않고 점수에서
 * 다시 유도한다({@link JobMatch}). 요구 역량이 없는 공고(아직 분석 전)는 결과를 저장하지 않는다 — 0% 를 저장하면 목록
 * 카드가 "매칭 안 됨" 대신 "0%" 를 보여 준다.
 */
@Component
public class MatchTaskHandler implements AiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchTaskHandler.class);

    private final AiProviderClient aiClient;
    private final PostingMatchQueryRepository matches;
    private final JobMatchRepository jobMatches;
    private final UserRepository users;
    private final JobPostingRepository postings;
    private final PromptVersionRegistry promptVersions;
    private final ObjectMapper objectMapper;

    public MatchTaskHandler(AiProviderClient aiClient, PostingMatchQueryRepository matches,
            JobMatchRepository jobMatches, UserRepository users, JobPostingRepository postings,
            PromptVersionRegistry promptVersions, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.matches = matches;
        this.jobMatches = jobMatches;
        this.users = users;
        this.postings = postings;
        this.promptVersions = promptVersions;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTaskType type() {
        return AiTaskType.MATCH;
    }

    @Override
    public Result handle(AiTask task) {
        Long userId = task.getUserId();
        Long postingId = task.getJobPostingId();

        List<Requirement> requirements = matches.findRequirements(postingId);
        if (requirements.isEmpty()) {
            log.debug("공고 {} 는 요구 역량이 없어 매칭을 저장하지 않는다", postingId);
            return new Result("none", promptVersions.of(AiTaskType.MATCH),
                    objectMapper.writeValueAsString(new MatchResult(postingId, null, null)));
        }

        List<ExperienceInput> inputs = matches.findExperienceInputs(userId);
        String inputHash = MatchInputHash.of(requirements, inputs);

        MatchResponse response = aiClient.match(request(postingId, userId, requirements, inputs));

        Map<String, BigDecimal> strengthOf = inputs.stream().collect(Collectors.toMap(
                input -> input.experienceId() + ":" + input.competencyId(), ExperienceInput::strength, (a, b) -> a));
        List<CoverageRow> coverage = new ArrayList<>();
        int coveredCount = 0;
        for (MatchResponse.MatchRow row : response.rows()) {
            List<CoverageExperience> experiences = row.experienceIds().stream()
                    .map(id -> new CoverageExperience(id,
                            strengthOf.getOrDefault(id + ":" + row.competencyId(), BigDecimal.ZERO)))
                    .toList();
            coverage.add(new CoverageRow(row.competencyId(), row.weight(), row.score(), row.isGap(), experiences));
            if (!row.isGap()) {
                coveredCount++;
            }
        }

        BigDecimal score = response.overall().max(BigDecimal.ZERO).min(BigDecimal.ONE)
                .setScale(3, RoundingMode.HALF_UP);
        String coverageJson = objectMapper.writeValueAsString(coverage);
        int covered = coveredCount;
        JobMatch match = jobMatches.findByUserIdAndJobPostingId(userId, postingId)
                .map(existing -> {
                    existing.recompute(score, covered, coverageJson, inputHash);
                    return existing;
                })
                .orElseGet(() -> jobMatches.save(JobMatch.compute(
                        users.getReferenceById(userId), postings.getReferenceById(postingId),
                        score, covered, coverageJson, inputHash)));

        int percent = score.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
        String resultPayload = objectMapper.writeValueAsString(
                new MatchResult(postingId, percent, match.getVerdict().name()));
        return new Result(response.model(), response.promptVersion(), resultPayload);
    }

    private MatchRequest request(Long postingId, Long userId, List<Requirement> requirements,
            List<ExperienceInput> inputs) {
        Map<Long, List<ExperienceInput>> byExperience = inputs.stream().collect(Collectors.groupingBy(
                ExperienceInput::experienceId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ExperienceView> views = matches.findExperiences(userId, byExperience.keySet()).stream()
                .collect(Collectors.toMap(ExperienceView::id, Function.identity()));

        List<MatchExperience> experiences = byExperience.entrySet().stream()
                .map(entry -> {
                    ExperienceView view = views.get(entry.getKey());
                    return new MatchExperience(
                            entry.getKey(),
                            view == null || view.title() == null || view.title().isBlank()
                                    ? "경험 " + entry.getKey() : view.title(),
                            view == null || view.result() == null ? "" : view.result(),
                            entry.getValue().stream()
                                    .map(input -> new ExperienceStrength(input.competencyId(), input.strength()))
                                    .toList());
                })
                .toList();

        return new MatchRequest(
                new MatchPosting(postingId, requirements.stream()
                        .map(r -> new MatchRequirement(r.competencyId(), r.weight(),
                                r.evidenceLine() == null ? "" : r.evidenceLine()))
                        .toList()),
                experiences);
    }

    /** {@code job_matches.coverage} 한 행. {@code PostingMatchService.parseCoverage} 가 읽는 모양이다. */
    record CoverageRow(Long competencyId, BigDecimal weight, BigDecimal score, boolean isGap,
            List<CoverageExperience> experiences) {
    }

    record CoverageExperience(Long id, BigDecimal strength) {
    }

    /** 폴링 응답의 result — 명세 §6: MATCH {postingId, score, verdict}. 분석 전 공고는 score·verdict 가 null. */
    record MatchResult(Long postingId, Integer score, String verdict) {
    }
}
