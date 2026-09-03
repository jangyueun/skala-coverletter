package com.team.careerfit.job.service;

import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.job.dto.PostingMatchResponse;
import com.team.careerfit.job.dto.PostingMatchResponse.Experience;
import com.team.careerfit.job.dto.PostingMatchResponse.MatchStatus;
import com.team.careerfit.job.dto.PostingMatchResponse.Row;
import com.team.careerfit.job.exception.JobException;
import com.team.careerfit.job.repository.PostingMatchQueryRepository;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.ExperienceInput;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.ExperienceView;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.Requirement;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.StoredMatch;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.Task;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PostingMatchService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String PROMPT_VERSION = "match/v1";

    private final PostingMatchQueryRepository matches;
    private final ObjectMapper objectMapper;

    public PostingMatchService(PostingMatchQueryRepository matches, ObjectMapper objectMapper) {
        this.matches = matches;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PostingMatchResponse findOrRequest(Long userId, Long postingId) {
        if (!matches.postingExists(postingId)) {
            throw JobException.postingNotFound();
        }

        List<Requirement> requirements = matches.findRequirements(postingId);
        if (requirements.isEmpty()) {
            return empty(MatchStatus.NOT_COMPUTED, null, 0);
        }

        List<ExperienceInput> experienceInputs = matches.findExperienceInputs(userId);
        String inputHash = inputHash(requirements, experienceInputs);
        StoredMatch stored = matches.findMatch(postingId, userId).orElse(null);
        Task task = matches.findLatestTask(postingId, userId, inputHash).orElse(null);
        if (stored != null && stored.inputHash().equals(inputHash)) {
            return completed(userId, requirements, stored, task == null ? null : task.id());
        }
        if (task != null && task.status().isInFlight()) {
            return empty(status(task.status()), task.id(), requirements.size());
        }
        if (task != null && task.status() == AiTaskStatus.FAILED) {
            return empty(MatchStatus.FAILED, task.id(), requirements.size());
        }

        String idempotencyKey = sha256("MATCH:" + userId + ":" + postingId + ":" + inputHash + ":" + PROMPT_VERSION);
        Long taskId = matches.createTask(
                postingId,
                userId,
                idempotencyKey,
                inputHash,
                requestPayload(postingId, requirements, experienceInputs));
        return empty(MatchStatus.PENDING, taskId, requirements.size());
    }

    private PostingMatchResponse completed(
            Long userId,
            List<Requirement> requirements,
            StoredMatch stored,
            Long taskId) {
        Map<Long, Coverage> coverage = parseCoverage(stored.coverage());
        Set<Long> experienceIds = new LinkedHashSet<>();
        coverage.values().forEach(item -> item.experiences().forEach(value -> experienceIds.add(value.id())));
        Map<Long, ExperienceView> experiences = new HashMap<>();
        matches.findExperiences(userId, experienceIds).forEach(value -> experiences.put(value.id(), value));

        List<Row> rows = requirements.stream().map(requirement -> {
            Coverage item = coverage.getOrDefault(requirement.competencyId(), Coverage.empty());
            List<Experience> rowExperiences = item.experiences().stream()
                    .filter(value -> experiences.containsKey(value.id()))
                    .map(value -> {
                        ExperienceView experience = experiences.get(value.id());
                        return new Experience(
                                experience.id(),
                                experience.title(),
                                experience.result(),
                                value.strength());
                    })
                    .toList();
            return new Row(
                    requirement.competencyId(),
                    requirement.name(),
                    requirement.category(),
                    requirement.weight(),
                    item.score(),
                    item.gap(),
                    requirement.evidenceLine(),
                    rowExperiences);
        }).toList();

        return new PostingMatchResponse(
                MatchStatus.COMPLETED,
                taskId,
                percent(stored.matchScore()),
                stored.verdict(),
                stored.coveredCount(),
                requirements.size(),
                stored.updatedAt().atZone(KOREA).toOffsetDateTime(),
                rows);
    }

    private PostingMatchResponse empty(MatchStatus status, Long taskId, int requiredCount) {
        return new PostingMatchResponse(
                status,
                taskId,
                null,
                null,
                null,
                requiredCount,
                null,
                List.of());
    }

    private Map<Long, Coverage> parseCoverage(String json) {
        try {
            Map<Long, Coverage> result = new LinkedHashMap<>();
            for (JsonNode node : objectMapper.readTree(json)) {
                List<CoverageExperience> experiences = new ArrayList<>();
                JsonNode experienceNodes = node.path("experiences");
                if (experienceNodes.isArray()) {
                    for (JsonNode experience : experienceNodes) {
                        experiences.add(new CoverageExperience(
                                experience.path("id").asLong(),
                                experience.path("strength").decimalValue()));
                    }
                }
                result.put(
                        node.path("competencyId").asLong(),
                        new Coverage(
                                node.path("score").decimalValue(),
                                node.path("isGap").asBoolean(),
                                experiences));
            }
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("저장된 매칭 결과 형식이 올바르지 않습니다.", exception);
        }
    }

    private String inputHash(List<Requirement> requirements, List<ExperienceInput> experiences) {
        StringBuilder input = new StringBuilder();
        requirements.forEach(value -> input.append("R:")
                .append(value.competencyId()).append(':')
                .append(value.weight().stripTrailingZeros().toPlainString()).append(';'));
        experiences.forEach(value -> input.append("E:")
                .append(value.experienceId()).append(':')
                .append(value.competencyId()).append(':')
                .append(value.strength().stripTrailingZeros().toPlainString()).append(';'));
        return sha256(input.toString());
    }

    private String requestPayload(
            Long postingId,
            List<Requirement> requirements,
            List<ExperienceInput> experiences) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "postingId", postingId,
                    "required", requirements,
                    "experiences", experiences));
        } catch (JacksonException exception) {
            throw new IllegalStateException("매칭 요청을 만들 수 없습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private int percent(BigDecimal score) {
        return score.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private MatchStatus status(AiTaskStatus status) {
        return MatchStatus.valueOf(status.name());
    }

    private record Coverage(BigDecimal score, boolean gap, List<CoverageExperience> experiences) {

        private static Coverage empty() {
            return new Coverage(BigDecimal.ZERO, true, List.of());
        }
    }

    private record CoverageExperience(Long id, BigDecimal strength) {
    }
}
