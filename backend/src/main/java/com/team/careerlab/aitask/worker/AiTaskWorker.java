package com.team.careerlab.aitask.worker;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.repository.AiTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING 인 AiTask 를 주기적으로 걷어가 {@link AiTaskHandler} 로 처리한다.
 *
 * <p>타입별 처리기가 아직 없으면(도메인이 아직 안 붙었으면) 그 타입은 건드리지 않고 PENDING 으로
 * 둔다 — 처리기를 나중에 추가해도 이미 쌓인 작업이 그대로 처리된다.
 *
 * <p>실제 처리·트랜잭션은 {@link AiTaskProcessor} 가 맡는다. 이 클래스가 그 메서드를 직접
 * 갖지 않는 이유는 self-invocation 때문이다 — 같은 빈 안에서 {@code @Transactional} 메서드를
 * 자기 자신이 호출하면 프록시를 안 거쳐 트랜잭션이 안 걸린다.
 */
@Component
public class AiTaskWorker {

    private final AiTaskRepository aiTasks;
    private final AiTaskProcessor processor;
    private final Map<AiTaskType, AiTaskHandler> handlers;

    public AiTaskWorker(AiTaskRepository aiTasks, AiTaskProcessor processor, List<AiTaskHandler> handlerList) {
        this.aiTasks = aiTasks;
        this.processor = processor;
        this.handlers = handlerList.stream().collect(Collectors.toMap(AiTaskHandler::type, Function.identity()));
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void processPending() {
        for (AiTask task : aiTasks.findByStatusOrderByCreatedAt(AiTaskStatus.PENDING)) {
            AiTaskHandler handler = handlers.get(task.getTaskType());
            if (handler != null) {
                processor.process(task.getId(), handler);
            }
        }
    }
}
