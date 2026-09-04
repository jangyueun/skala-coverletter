package com.team.careerlab.internal.service;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.repository.AiTaskRepository;
import com.team.careerlab.global.config.InternalApiProperties;
import com.team.careerlab.internal.exception.InternalApiException;
import com.team.careerlab.internal.repository.PostingAnalysisInputRepository;
import com.team.careerlab.internal.repository.PostingAnalysisInputRepository.PostingSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PostingAnalysisService {

    private static final String PROMPT_VERSION = "posting_analysis/v2";
    private static final List<AiTaskStatus> IN_FLIGHT = List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING);

    private final InternalApiProperties properties;
    private final PostingAnalysisInputRepository inputs;
    private final AiTaskRepository tasks;
    private final ObjectMapper objectMapper;

    public PostingAnalysisService(
            InternalApiProperties properties,
            PostingAnalysisInputRepository inputs,
            AiTaskRepository tasks,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.inputs = inputs;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EnqueueResult enqueue(Long postingId, String token) {
        requireValidToken(token);

        PostingSnapshot posting = inputs.findPosting(postingId)
                .orElseThrow(InternalApiException::postingNotFound);
        String payload = json(new PostingAnalysisRequest(
                posting.id(), posting.content(), inputs.findCompetencies()));
        String inputHash = sha256(payload);
        String idempotencyKey = sha256("POSTING_ANALYSIS:" + postingId + ":" + inputHash + ":" + PROMPT_VERSION);

        var sameInput = tasks.findByIdempotencyKey(idempotencyKey);
        if (sameInput.isPresent()) {
            return new EnqueueResult(sameInput.get().getId(), false);
        }

        var inFlight = tasks.findFirstByTaskTypeAndJobPostingIdAndStatusInOrderByCreatedAtDesc(
                AiTaskType.POSTING_ANALYSIS, postingId, IN_FLIGHT);
        if (inFlight.isPresent()) {
            throw InternalApiException.analysisAlreadyRunning();
        }

        AiTask saved = tasks.save(AiTask.postingAnalysis(postingId, idempotencyKey, inputHash, payload));
        return new EnqueueResult(saved.getId(), true);
    }

    private void requireValidToken(String actualToken) {
        String expectedToken = properties.token();
        boolean valid = expectedToken != null
                && !expectedToken.isBlank()
                && actualToken != null
                && MessageDigest.isEqual(
                        expectedToken.getBytes(StandardCharsets.UTF_8),
                        actualToken.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw InternalApiException.invalidToken();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("공고 분석 입력을 JSON으로 만들 수 없습니다.", exception);
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

    public record EnqueueResult(Long taskId, boolean created) {
    }

    private record PostingAnalysisRequest(
            Long postingId,
            String content,
            List<PostingAnalysisInputRepository.CompetencySnapshot> competencies) {
    }
}
