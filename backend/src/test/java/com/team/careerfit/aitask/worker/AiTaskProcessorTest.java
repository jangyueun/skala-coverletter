package com.team.careerfit.aitask.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import com.team.careerfit.integration.ai.exception.AiProviderException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiTaskProcessorTest {

    private final AiTaskRepository aiTasks = mock(AiTaskRepository.class);
    private final AiTaskProcessor processor = new AiTaskProcessor(aiTasks, new JsonMapper());

    @Test
    void 처리기가_성공하면_COMPLETED로_바뀐다() {
        AiTask task = AiTask.postingAnalysis(9L, "key", "hash", "{}");
        setId(task, 1L);
        when(aiTasks.findById(1L)).thenReturn(Optional.of(task));

        AiTaskHandler handler = stubHandler(AiTaskType.POSTING_ANALYSIS,
                t -> new AiTaskHandler.Result("mock-ai", "posting_analysis/v2", "{\"ok\":true}"));

        processor.process(1L, handler);

        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThat(task.getModel()).isEqualTo("mock-ai");
        assertThat(task.getResultPayload()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void 처리기가_계속_실패하면_3번_시도하고_FAILED로_남긴다() {
        AiTask task = AiTask.draft(7L, 31L, "key", "hash", "{}");
        setId(task, 2L);
        when(aiTasks.findById(2L)).thenReturn(Optional.of(task));

        AiTaskHandler handler = mock(AiTaskHandler.class);
        when(handler.type()).thenReturn(AiTaskType.DRAFT);
        when(handler.handle(any())).thenThrow(new AiProviderException("실패", new RuntimeException()));

        processor.process(2L, handler);

        verify(handler, times(3)).handle(any());
        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(task.getErrorCode()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(task.getRetryCount()).isEqualTo(3);
    }

    @Test
    void 이미_PENDING이_아니면_건드리지_않는다() {
        AiTask task = AiTask.draft(7L, 31L, "key", "hash", "{}");
        setId(task, 3L);
        task.start();
        task.complete("m", "v", "{}", "[]");
        when(aiTasks.findById(3L)).thenReturn(Optional.of(task));

        AiTaskHandler handler = mock(AiTaskHandler.class);
        when(handler.type()).thenReturn(AiTaskType.DRAFT);

        processor.process(3L, handler);

        verify(handler, times(0)).handle(any());
    }

    private AiTaskHandler stubHandler(AiTaskType type, java.util.function.Function<AiTask, AiTaskHandler.Result> fn) {
        AiTaskHandler handler = mock(AiTaskHandler.class);
        when(handler.type()).thenReturn(type);
        when(handler.handle(any())).thenAnswer(invocation -> fn.apply(invocation.getArgument(0)));
        return handler;
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
