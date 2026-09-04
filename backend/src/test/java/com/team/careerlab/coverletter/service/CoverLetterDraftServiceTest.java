package com.team.careerlab.coverletter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.coverletter.exception.CoverLetterException;
import com.team.careerlab.experience.service.ExperienceService;
import com.team.careerlab.job.repository.JobPostingQuestionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CoverLetterDraftServiceTest {

    private final JobPostingQuestionRepository questions = mock(JobPostingQuestionRepository.class);
    private final ExperienceService experiences = mock(ExperienceService.class);
    private final AiTaskService aiTasks = mock(AiTaskService.class);
    private final CoverLetterDraftService service = new CoverLetterDraftService(questions, experiences, aiTasks);

    @Test
    void 문항과_경험_소유가_확인되면_초안_작업을_만든다() {
        when(questions.existsById(31L)).thenReturn(true);
        when(experiences.allOwnedBy(7L, List.of(1L, 4L))).thenReturn(true);
        when(aiTasks.reserveDraftTask(eq(7L), eq(31L), anyString()))
                .thenReturn(new AiTaskService.Reservation(821L, true));

        CoverLetterDraftService.Result result = service.requestDraft(7L, 31L, List.of(1L, 4L));

        assertThat(result.taskId()).isEqualTo(821L);
        assertThat(result.created()).isTrue();
    }

    @Test
    void 존재하지_않는_문항이면_거부한다() {
        when(questions.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.requestDraft(7L, 99L, List.of(1L)))
                .isInstanceOf(CoverLetterException.class)
                .extracting(e -> ((CoverLetterException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(aiTasks, never()).reserveDraftTask(anyLong(), anyLong(), anyString());
    }

    @Test
    void 남의_경험을_근거로_쓰면_거부한다() {
        when(questions.existsById(31L)).thenReturn(true);
        when(experiences.allOwnedBy(7L, List.of(999L))).thenReturn(false);

        assertThatThrownBy(() -> service.requestDraft(7L, 31L, List.of(999L)))
                .isInstanceOf(CoverLetterException.class)
                .extracting(e -> ((CoverLetterException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(aiTasks, never()).reserveDraftTask(anyLong(), anyLong(), anyString());
    }
}
