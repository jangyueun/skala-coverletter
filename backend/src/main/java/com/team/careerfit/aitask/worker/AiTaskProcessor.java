package com.team.careerfit.aitask.worker;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 작업 하나를 실제로 처리한다. {@link AiTaskWorker} 에서 이 클래스를 따로 뺀 이유 —
 * 같은 빈 안에서 {@code @Transactional} 메서드를 자기 자신이 호출하면(self-invocation) 프록시를
 * 안 거쳐서 트랜잭션이 안 걸린다. 스케줄러(호출자)와 트랜잭션 경계(이 클래스)를 빈으로 분리해야
 * {@code @Transactional} 이 실제로 적용된다.
 */
@Component
public class AiTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiTaskProcessor.class);
    private static final int MAX_ATTEMPTS = 3;

    private final AiTaskRepository aiTasks;
    private final ObjectMapper objectMapper;

    public AiTaskProcessor(AiTaskRepository aiTasks, ObjectMapper objectMapper) {
        this.aiTasks = aiTasks;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(Long taskId, AiTaskHandler handler) {
        AiTask task = aiTasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != AiTaskStatus.PENDING) {
            return; // 다른 스케줄 틱이 이미 집어갔거나 지워졌다
        }
        task.start();

        List<Attempt> attempts = new ArrayList<>();
        for (int attemptNo = 1; attemptNo <= MAX_ATTEMPTS; attemptNo++) {
            long startedAt = System.currentTimeMillis();
            try {
                AiTaskHandler.Result result = handler.handle(task);
                task.complete(result.model(), result.promptVersion(), result.resultPayload(), writeAttempts(attempts));
                return;
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startedAt;
                attempts.add(new Attempt(attemptNo, "FAILED", latencyMs, errorCode(e), Instant.now().toString()));
                log.warn("AI 작업 처리 실패 taskId={} type={} attempt={}/{}", task.getId(), task.getTaskType(),
                        attemptNo, MAX_ATTEMPTS, e);
                task.recordRetry(writeAttempts(attempts));
                if (attemptNo < MAX_ATTEMPTS) {
                    sleep(200L * attemptNo);
                }
            }
        }
        task.fail("AI_PROVIDER_ERROR", "AI 제공자 호출에 반복 실패했습니다.", writeAttempts(attempts));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String errorCode(Exception e) {
        return e instanceof com.team.careerfit.integration.ai.exception.AiProviderException
                ? "AI_PROVIDER_ERROR" : "UNKNOWN_ERROR";
    }

    private String writeAttempts(List<Attempt> attempts) {
        return objectMapper.writeValueAsString(attempts);
    }

    private record Attempt(int no, String status, long latencyMs, String errorCode, String at) {
    }
}
