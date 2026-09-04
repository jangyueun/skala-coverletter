package com.team.careerlab.aitask.worker;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.entity.AiTask;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.repository.AiTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTaskWorkerTest {

    private final AiTaskRepository aiTasks = mock(AiTaskRepository.class);
    private final AiTaskProcessor processor = mock(AiTaskProcessor.class);

    @Test
    void 처리기가_있는_타입만_넘긴다() {
        AiTask analysisTask = AiTask.postingAnalysis(9L, "key1", "hash1", "{}");
        setId(analysisTask, 1L);
        AiTask matchTask = AiTask.match(7L, 9L, "key2", "hash2", "{}"); // 아직 처리기가 없는 타입
        setId(matchTask, 2L);
        when(aiTasks.findByStatusOrderByCreatedAt(AiTaskStatus.PENDING)).thenReturn(List.of(analysisTask, matchTask));

        AiTaskHandler analysisHandler = mock(AiTaskHandler.class);
        when(analysisHandler.type()).thenReturn(AiTaskType.POSTING_ANALYSIS);

        AiTaskWorker worker = new AiTaskWorker(aiTasks, processor, List.of(analysisHandler));
        worker.processPending();

        verify(processor, times(1)).process(eq(1L), eq(analysisHandler));
        verify(processor, never()).process(eq(2L), org.mockito.ArgumentMatchers.any());
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
