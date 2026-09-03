package com.team.careerfit.aitask.service;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
