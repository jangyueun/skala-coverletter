package com.team.careerfit.aitask.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.exception.AiTaskException;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiTaskServiceTest {

    private final AiTaskRepository aiTasks = mock(AiTaskRepository.class);
    private final AiTaskService service = new AiTaskService(aiTasks);

    @Test
    void 진행_중인_작업이_없으면_새로_만든다() {
        when(aiTasks.findFirstByTaskTypeAndUserIdAndStatusIn(AiTaskType.EXPERIENCE_INTAKE, 7L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.empty());
        when(aiTasks.save(any(AiTask.class))).thenAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            setId(task, 790L);
            return task;
        });

        AiTaskService.Reservation reservation = service.reserveIntakeTask(7L, "hash-a", "{}");

        assertThat(reservation.taskId()).isEqualTo(790L);
        assertThat(reservation.created()).isTrue();
    }

    @Test
    void 같은_입력으로_진행_중이면_기존_작업을_재사용한다() {
        AiTask inFlight = AiTask.experienceIntake(7L, "key", "hash-a", "{}");
        setId(inFlight, 790L);
        when(aiTasks.findFirstByTaskTypeAndUserIdAndStatusIn(AiTaskType.EXPERIENCE_INTAKE, 7L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.of(inFlight));

        AiTaskService.Reservation reservation = service.reserveIntakeTask(7L, "hash-a", "{}");

        assertThat(reservation.taskId()).isEqualTo(790L);
        assertThat(reservation.created()).isFalse();
        verify(aiTasks, never()).save(any());
    }

    @Test
    void 다른_입력으로_진행_중이면_거부한다() {
        AiTask inFlight = AiTask.experienceIntake(7L, "key", "hash-a", "{}");
        setId(inFlight, 790L);
        when(aiTasks.findFirstByTaskTypeAndUserIdAndStatusIn(AiTaskType.EXPERIENCE_INTAKE, 7L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> service.reserveIntakeTask(7L, "hash-b", "{}"))
                .isInstanceOf(AiTaskException.class)
                .extracting(e -> ((AiTaskException) e).status())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(aiTasks, never()).save(any());
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
