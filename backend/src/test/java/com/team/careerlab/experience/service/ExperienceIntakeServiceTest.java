package com.team.careerlab.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.experience.exception.ExperienceException;
import com.team.careerlab.integration.storage.client.SupabaseStorageClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

class ExperienceIntakeServiceTest {

    private final AiTaskService aiTasks = mock(AiTaskService.class);
    private final SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    private final ExperienceIntakeService service = new ExperienceIntakeService(aiTasks, storage, new JsonMapper());

    @Test
    void 링크만_있어도_작업을_만든다() {
        when(aiTasks.reserveIntakeTask(eq(7L), anyString(), anyString()))
                .thenReturn(new AiTaskService.Reservation(790L, true));

        ExperienceIntakeService.Result result = service.intake(7L, "https://github.com/example", List.of());

        assertThat(result.taskId()).isEqualTo(790L);
        assertThat(result.created()).isTrue();
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void 링크와_파일이_모두_없으면_거부한다() {
        assertThatThrownBy(() -> service.intake(7L, null, List.of()))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 허용되지_않는_확장자면_거부한다() {
        MultipartFile file = new MockMultipartFile("files", "malware.exe", "application/octet-stream",
                "content".getBytes());

        assertThatThrownBy(() -> service.intake(7L, null, List.of(file)))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).code())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 파일이_10MB를_넘으면_거부한다() {
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("files", "portfolio.pdf", "application/pdf", tooBig);

        assertThatThrownBy(() -> service.intake(7L, null, List.of(file)))
                .isInstanceOf(ExperienceException.class)
                .extracting(e -> ((ExperienceException) e).code())
                .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 파일이_있으면_taskId_경로로_업로드하고_페이로드를_다시_채운다() {
        MultipartFile file = new MockMultipartFile("files", "portfolio.pdf", "application/pdf", "hello".getBytes());
        when(aiTasks.reserveIntakeTask(eq(7L), anyString(), anyString()))
                .thenReturn(new AiTaskService.Reservation(790L, true));
        when(storage.upload(eq("intake/7/790/portfolio.pdf"), any(byte[].class), anyString()))
                .thenReturn("https://x.supabase.co/storage/v1/object/public/intake/portfolio.pdf");

        ExperienceIntakeService.Result result = service.intake(7L, null, List.of(file));

        assertThat(result.created()).isTrue();
        verify(aiTasks).attachIntakePayload(eq(790L), anyString());
    }

    @Test
    void 같은_입력이_진행_중이면_업로드하지_않는다() {
        MultipartFile file = new MockMultipartFile("files", "portfolio.pdf", "application/pdf", "hello".getBytes());
        when(aiTasks.reserveIntakeTask(eq(7L), anyString(), anyString()))
                .thenReturn(new AiTaskService.Reservation(790L, false));

        ExperienceIntakeService.Result result = service.intake(7L, null, List.of(file));

        assertThat(result.taskId()).isEqualTo(790L);
        assertThat(result.created()).isFalse();
        verify(storage, never()).upload(anyString(), any(), anyString());
        verify(aiTasks, never()).attachIntakePayload(any(), anyString());
    }
}
