package com.team.careerfit.aitask.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.aitask.dto.AiTaskListResponse;
import com.team.careerfit.aitask.dto.AiTaskResponse;
import com.team.careerfit.aitask.entity.AiTask;
import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.aitask.entity.AiTaskType;
import com.team.careerfit.aitask.exception.AiTaskException;
import com.team.careerfit.aitask.repository.AiTaskRepository;
import com.team.careerfit.integration.ai.client.PromptVersionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class AiTaskServiceTest {

    private final AiTaskRepository aiTasks = mock(AiTaskRepository.class);
    private final PromptVersionRegistry promptVersions = mock(PromptVersionRegistry.class);
    private final AiTaskService service = new AiTaskService(aiTasks, promptVersions, new JsonMapper());

    @Test
    void 멱등_키에_프롬프트_버전이_들어간다_버전이_바뀌면_같은_입력도_새_작업이다() {
        when(aiTasks.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(aiTasks.save(any(AiTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(promptVersions.of(AiTaskType.MATCH)).thenReturn("match/v1");
        AiTask first = service.createMatchTask(7L, 9L, "{}");
        when(promptVersions.of(AiTaskType.MATCH)).thenReturn("match/v2");
        AiTask second = service.createMatchTask(7L, 9L, "{}");

        assertThat(first.getIdempotencyKey()).isNotEqualTo(second.getIdempotencyKey());
        assertThat(first.getInputHash()).isEqualTo(second.getInputHash());   // 입력 해시는 프롬프트와 무관
    }

    @Test
    void 조회는_만든_사용자만_할_수_있다() {
        AiTask task = AiTask.draft(7L, 31L, "key", "hash", "{}");
        setId(task, 821L);
        task.start();
        task.complete("claude-opus-5", "draft/v1", "{\"draft\":\"초안\",\"charCount\":2}", "[]");
        when(aiTasks.findById(821L)).thenReturn(Optional.of(task));

        AiTaskResponse response = service.find(7L, 821L);
        assertThat(response.status()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThat(response.result().get("draft").asString()).isEqualTo("초안");
        assertThat(response.attempts()).isEqualTo(1);
        assertThat(response.error()).isNull();

        assertThatThrownBy(() -> service.find(8L, 821L))
                .isInstanceOf(AiTaskException.class)
                .extracting(e -> ((AiTaskException) e).code())
                .isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> service.find(7L, 999L))
                .isInstanceOf(AiTaskException.class)
                .extracting(e -> ((AiTaskException) e).code())
                .isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void 실패한_작업은_error_를_같이_준다() {
        AiTask task = AiTask.experienceIntake(7L, "key", "hash", "{}");
        setId(task, 790L);
        task.start();
        task.recordRetry("[{\"no\":1}]");
        task.recordRetry("[{\"no\":1},{\"no\":2}]");
        task.fail("AI_PROVIDER_ERROR", "AI 제공자 호출에 반복 실패했습니다.", "[{\"no\":1},{\"no\":2},{\"no\":3}]");
        when(aiTasks.findById(790L)).thenReturn(Optional.of(task));

        AiTaskResponse response = service.find(7L, 790L);

        assertThat(response.status()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(response.error().code()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(response.result()).isNull();
    }

    @Test
    void 목록은_타입_상태_시각으로_거른다() {
        AiTask draft = AiTask.draft(7L, 31L, "k1", "h", "{}");
        AiTask intake = AiTask.experienceIntake(7L, "k2", "h", "{}");
        intake.start();
        when(aiTasks.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(draft, intake));

        AiTaskListResponse all = service.list(7L, null, Set.of(), null);
        assertThat(all.counts().pending()).isEqualTo(1);
        assertThat(all.counts().running()).isEqualTo(1);
        assertThat(all.items()).hasSize(2);

        AiTaskListResponse onlyRunning = service.list(7L, null, Set.of(AiTaskStatus.RUNNING), null);
        assertThat(onlyRunning.items()).extracting(AiTaskListResponse.Item::type)
                .containsExactly(AiTaskType.EXPERIENCE_INTAKE);
        assertThat(service.list(7L, AiTaskType.DRAFT, Set.of(), null).items()).hasSize(1);
        assertThat(service.list(7L, null, Set.of(), Instant.now().plusSeconds(60)).items()).isEmpty();
    }

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
