package com.team.careerfit.aitask.service;

import com.team.careerfit.aitask.dto.AiTaskListResponse;
import com.team.careerfit.aitask.dto.AiTaskResponse;
import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.exception.AiTaskException;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import com.team.careerfit.integration.ai.client.PromptVersionRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 작업 생성과 조회.
 *
 * <p>생성은 PENDING 행을 만들고 taskId 를 돌려주는 것까지다. 실제 처리는 {@code AiTaskWorker} 가 타입별
 * {@code AiTaskHandler} 로 한다. 조회({@code GET /api/ai-tasks/**})는 만든 사용자만 볼 수 있다.
 *
 * <p>멱등 키에는 {@link PromptVersionRegistry} 의 프롬프트 버전이 들어간다(명세 §8) — 프롬프트를 고치면 같은 입력이라도
 * 새 작업이 만들어진다.
 */
@Service
public class AiTaskService {

    private final AiTaskRepository aiTasks;
    private final PromptVersionRegistry promptVersions;
    private final ObjectMapper objectMapper;

    public AiTaskService(AiTaskRepository aiTasks, PromptVersionRegistry promptVersions, ObjectMapper objectMapper) {
        this.aiTasks = aiTasks;
        this.promptVersions = promptVersions;
        this.objectMapper = objectMapper;
    }

    /**
     * MATCH 는 같은 대상(사용자·공고)에 여러 작업이 동시에 진행 중이어도 되는 타입이라
     * (부분 UNIQUE 가 걸려 있지 않다) 같은 입력이면 재사용하고, 아니면 그냥 새로 만든다.
     */
    @Transactional
    public AiTask createMatchTask(Long userId, Long jobPostingId, String requestPayload) {
        String inputHash = sha256(requestPayload);
        String idempotencyKey = sha256("MATCH|" + userId + "|" + jobPostingId + "|" + inputHash + "|"
                + promptVersions.of(AiTaskType.MATCH));
        return aiTasks.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> aiTasks.save(
                        AiTask.match(userId, jobPostingId, idempotencyKey, inputHash, requestPayload)));
    }

    /**
     * 인테이크는 같은 사용자에게 진행 중(PENDING·RUNNING) 작업이 있으면 새로 만들지 않는다.
     * 입력이 같으면(inputHash) 그 작업을 재사용(200)하고, 다르면 409 로 막는다.
     *
     * <p>Storage 경로에 taskId 가 필요해서 여기서는 links 뿐인 스냅샷으로 행만 만든다. 파일을
     * 다 올린 뒤에는 {@link #attachIntakePayload} 로 fileUrls 를 채운 스냅샷으로 덮어써야 한다.
     *
     * @throws AiTaskException 다른 입력의 작업이 이미 진행 중이면 {@code INTAKE_ALREADY_RUNNING}
     */
    @Transactional
    public Reservation reserveIntakeTask(Long userId, String inputHash, String initialPayload) {
        Optional<AiTask> inFlight = aiTasks.findFirstByTaskTypeAndUserIdAndStatusIn(AiTaskType.EXPERIENCE_INTAKE,
                userId, List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING));

        if (inFlight.isPresent()) {
            AiTask task = inFlight.get();
            if (!task.getInputHash().equals(inputHash)) {
                throw AiTaskException.intakeAlreadyRunning();
            }
            return new Reservation(task.getId(), false);
        }

        String idempotencyKey = sha256("EXPERIENCE_INTAKE|" + userId + "|" + inputHash + "|"
                + promptVersions.of(AiTaskType.EXPERIENCE_INTAKE));
        return reserveByKey(idempotencyKey,
                () -> AiTask.experienceIntake(userId, idempotencyKey, inputHash, initialPayload));
    }

    /** 파일 업로드가 끝난 뒤 요청 스냅샷을 fileUrls 까지 포함한 값으로 다시 채운다. */
    @Transactional
    public void attachIntakePayload(Long taskId, String requestPayload) {
        AiTask task = aiTasks.getReferenceById(taskId);
        task.attachRequestPayload(requestPayload);
    }

    /**
     * 초안은 같은 (사용자, 문항)에 진행 중(PENDING·RUNNING) 작업이 있으면 새로 만들지 않는다.
     * 입력이 같으면(inputHash — 문항 + 근거 경험 선택) 그 작업을 재사용(200)하고, 다르면 409 로 막는다.
     */
    @Transactional
    public Reservation reserveDraftTask(Long userId, Long questionId, String requestPayload) {
        String inputHash = sha256(requestPayload);

        Optional<AiTask> inFlight = aiTasks.findFirstByTaskTypeAndUserIdAndQuestionIdAndStatusIn(
                AiTaskType.DRAFT, userId, questionId, List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING));

        if (inFlight.isPresent()) {
            AiTask task = inFlight.get();
            if (!task.getInputHash().equals(inputHash)) {
                throw AiTaskException.draftAlreadyRunning();
            }
            return new Reservation(task.getId(), false);
        }

        String idempotencyKey = sha256("DRAFT|" + userId + "|" + questionId + "|" + inputHash + "|"
                + promptVersions.of(AiTaskType.DRAFT));
        return reserveByKey(idempotencyKey,
                () -> AiTask.draft(userId, questionId, idempotencyKey, inputHash, requestPayload));
    }

    /**
     * 멱등 키로 기존 작업을 찾아 재사용하고, 없으면 만든다. 진행 중(PENDING·RUNNING)은 위에서 걸러진 뒤라
     * 여기 걸리는 건 끝난 작업뿐이다 —
     *   COMPLETED  → 그대로 재사용(같은 입력이면 같은 결과다). 프런트는 폴링해서 캐시된 결과를 바로 받는다.
     *   FAILED     → 되살려 다시 큐에 넣는다({@link AiTask#reopen}). 같은 자료로 재시도하는 길.
     *
     * <p>예전엔 이 확인 없이 {@code save()} 만 해서, 같은 입력을 두 번째 돌리면 유일 제약(uk_ai_tasks_idempotency_key)에
     * 걸려 500 이 났다. 완료된 인테이크·초안을 같은 입력으로 다시 요청할 때마다 터졌다.
     */
    private Reservation reserveByKey(String idempotencyKey, java.util.function.Supplier<AiTask> factory) {
        Optional<AiTask> existing = aiTasks.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            AiTask task = existing.get();
            if (task.getStatus() == AiTaskStatus.FAILED) {
                task.reopen();
                return new Reservation(task.getId(), true);
            }
            return new Reservation(task.getId(), false);
        }
        return new Reservation(aiTasks.save(factory.get()).getId(), true);
    }

    /**
     * 폴링 응답. 만든 사용자만 본다 — 공고 분석처럼 사용자가 없는 작업은 아무도 못 본다.
     *
     * @throws AiTaskException 없으면 {@code TASK_NOT_FOUND}, 남의 것이면 {@code FORBIDDEN}
     */
    @Transactional(readOnly = true)
    public AiTaskResponse find(Long userId, Long taskId) {
        AiTask task = aiTasks.findById(taskId).orElseThrow(AiTaskException::taskNotFound);
        if (!task.isOwnedBy(userId)) {
            throw AiTaskException.forbidden();
        }
        return AiTaskResponse.from(task, objectMapper);
    }

    /** 내 작업 현황. 필터는 전부 선택이다 — 없으면 전부. */
    @Transactional(readOnly = true)
    public AiTaskListResponse list(Long userId, AiTaskType type, Set<AiTaskStatus> statuses, Instant since) {
        List<AiTask> tasks = aiTasks.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(task -> type == null || task.getTaskType() == type)
                .filter(task -> statuses == null || statuses.isEmpty() || statuses.contains(task.getStatus()))
                .filter(task -> since == null || !task.getCreatedAt().isBefore(since))
                .toList();
        return AiTaskListResponse.of(tasks);
    }

    public record Reservation(Long taskId, boolean created) {
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 지원하지 않는 JVM 입니다.", e);
        }
    }
}
