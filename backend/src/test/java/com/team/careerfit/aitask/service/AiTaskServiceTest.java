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
    void 진행_중인_인테이크가_없으면_새로_만든다() {
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
    void 같은_입력의_인테이크가_진행_중이면_기존_작업을_재사용한다() {
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
    void 다른_입력의_인테이크가_진행_중이면_거부한다() {
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

    @Test
    void 진행_중인_초안이_없으면_새로_만든다() {
        when(aiTasks.findFirstByTaskTypeAndUserIdAndQuestionIdAndStatusIn(AiTaskType.DRAFT, 7L, 31L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.empty());
        when(aiTasks.save(any(AiTask.class))).thenAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            setId(task, 821L);
            return task;
        });

        AiTaskService.Reservation reservation = service.reserveDraftTask(7L, 31L, "{\"experienceIds\":[1]}");

        assertThat(reservation.taskId()).isEqualTo(821L);
        assertThat(reservation.created()).isTrue();
    }

    @Test
    void 같은_입력의_초안이_진행_중이면_기존_작업을_재사용한다() {
        AiTask inFlight = AiTask.draft(7L, 31L, "key", AiTaskService.sha256("payload"), "payload");
        setId(inFlight, 821L);
        when(aiTasks.findFirstByTaskTypeAndUserIdAndQuestionIdAndStatusIn(AiTaskType.DRAFT, 7L, 31L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.of(inFlight));

        AiTaskService.Reservation reservation = service.reserveDraftTask(7L, 31L, "payload");

        assertThat(reservation.taskId()).isEqualTo(821L);
        assertThat(reservation.created()).isFalse();
        verify(aiTasks, never()).save(any());
    }

    @Test
    void 다른_근거_경험으로_초안이_진행_중이면_거부한다() {
        AiTask inFlight = AiTask.draft(7L, 31L, "key", AiTaskService.sha256("payload-a"), "payload-a");
        setId(inFlight, 821L);
        when(aiTasks.findFirstByTaskTypeAndUserIdAndQuestionIdAndStatusIn(AiTaskType.DRAFT, 7L, 31L,
                List.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING))).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> service.reserveDraftTask(7L, 31L, "payload-b"))
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
