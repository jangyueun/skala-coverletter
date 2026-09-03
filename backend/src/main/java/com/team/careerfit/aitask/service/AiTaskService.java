package com.team.careerfit.aitask.service;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.exception.AiTaskException;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 작업 생성. <b>이번 범위는 PENDING 작업을 만들고 taskId 를 돌려주는 것까지다.</b>
 * MockAiClient 로 실제 완료 처리를 하는 워커는 별도 작업(AI 작업 섹션의 폴링 API들)에서 붙인다.
 *
 * <p>{@code idempotencyKey} 계산에 {@code promptVersion} 을 아직 넣지 않는다. AI 서버의
 * {@code GET /ai/prompts/versions} 연동 전이라 값을 구할 수 없다 — 연동이 붙으면 다시 계산해야 한다.
 */
@Service
public class AiTaskService {

    private final AiTaskRepository aiTasks;

    public AiTaskService(AiTaskRepository aiTasks) {
        this.aiTasks = aiTasks;
    }

    /**
     * MATCH 는 같은 대상(사용자·공고)에 여러 작업이 동시에 진행 중이어도 되는 타입이라
     * (부분 UNIQUE 가 걸려 있지 않다) 같은 입력이면 재사용하고, 아니면 그냥 새로 만든다.
     */
    @Transactional
    public AiTask createMatchTask(Long userId, Long jobPostingId, String requestPayload) {
        String inputHash = sha256(requestPayload);
        String idempotencyKey = sha256("MATCH|" + userId + "|" + jobPostingId + "|" + inputHash);
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

        String idempotencyKey = sha256("EXPERIENCE_INTAKE|" + userId + "|" + inputHash);
        AiTask task = aiTasks.save(AiTask.experienceIntake(userId, idempotencyKey, inputHash, initialPayload));
        return new Reservation(task.getId(), true);
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

        String idempotencyKey = sha256("DRAFT|" + userId + "|" + questionId + "|" + inputHash);
        AiTask task = aiTasks.save(AiTask.draft(userId, questionId, idempotencyKey, inputHash, requestPayload));
        return new Reservation(task.getId(), true);
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
