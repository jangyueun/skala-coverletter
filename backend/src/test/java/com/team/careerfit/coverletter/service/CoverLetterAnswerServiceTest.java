package com.team.careerfit.coverletter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.dto.CoverLetterAnswerSaveRequest;
import com.team.careerfit.coverletter.entity.CoverLetterAnswer;
import com.team.careerfit.coverletter.exception.CoverLetterException;
import com.team.careerfit.coverletter.repository.CoverLetterAnswerRepository;
import com.team.careerfit.experience.service.ExperienceService;
import com.team.careerfit.job.entity.JobPostingQuestion;
import com.team.careerfit.job.repository.JobPostingQuestionRepository;
import com.team.careerfit.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CoverLetterAnswerServiceTest {

    private final CoverLetterAnswerRepository answers = mock(CoverLetterAnswerRepository.class);
    private final JobPostingQuestionRepository questions = mock(JobPostingQuestionRepository.class);
    private final ExperienceService experiences = mock(ExperienceService.class);
    private final CoverLetterAnswerService service = new CoverLetterAnswerService(answers, questions, experiences);

    private final User user = user(7L);
    private final JobPostingQuestion question = mock(JobPostingQuestion.class);

    @Test
    void 저장된_답변이_없으면_빈_값을_돌려준다() {
        when(questions.existsById(31L)).thenReturn(true);
        when(answers.findByUserIdAndQuestionId(7L, 31L)).thenReturn(Optional.empty());

        CoverLetterAnswerResponse response = service.get(7L, 31L);

        assertThat(response.content()).isEmpty();
        assertThat(response.usedExperienceIds()).isEmpty();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void 저장된_답변이_있으면_그대로_돌려준다() {
        when(questions.existsById(31L)).thenReturn(true);
        CoverLetterAnswer answer = CoverLetterAnswer.write(user, question, "제 강점은...", List.of(1L, 4L), null);
        when(answers.findByUserIdAndQuestionId(7L, 31L)).thenReturn(Optional.of(answer));

        CoverLetterAnswerResponse response = service.get(7L, 31L);

        assertThat(response.questionId()).isEqualTo(31L);
        assertThat(response.content()).isEqualTo("제 강점은...");
        assertThat(response.usedExperienceIds()).containsExactly(1L, 4L);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void 조회_시_존재하지_않는_문항이면_거부한다() {
        when(questions.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(7L, 99L)).isInstanceOf(CoverLetterException.class);
    }

    @Test
    void 처음_저장하면_새로_만든다() {
        when(questions.findById(31L)).thenReturn(Optional.of(question));
        when(experiences.allOwnedBy(7L, List.of(1L, 4L))).thenReturn(true);
        when(answers.findByUserIdAndQuestionId(7L, 31L)).thenReturn(Optional.empty());
        when(answers.save(any(CoverLetterAnswer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CoverLetterAnswerResponse response = service.save(user, 31L,
                new CoverLetterAnswerSaveRequest("제 강점은...", List.of(1L, 4L), null));

        assertThat(response.content()).isEqualTo("제 강점은...");
        assertThat(response.usedExperienceIds()).containsExactly(1L, 4L);
    }

    @Test
    void 이미_있으면_덮어쓴다() {
        when(questions.findById(31L)).thenReturn(Optional.of(question));
        when(experiences.allOwnedBy(7L, List.of(1L))).thenReturn(true);
        CoverLetterAnswer existing = CoverLetterAnswer.write(user, question, "옛 내용", List.of(2L), null);
        when(answers.findByUserIdAndQuestionId(7L, 31L)).thenReturn(Optional.of(existing));

        CoverLetterAnswerResponse response = service.save(user, 31L,
                new CoverLetterAnswerSaveRequest("새 내용", List.of(1L), 821L));

        assertThat(response.content()).isEqualTo("새 내용");
        assertThat(response.usedExperienceIds()).containsExactly(1L);
        assertThat(response.aiTaskId()).isEqualTo(821L);
        verify(answers, never()).save(any());
    }

    @Test
    void 저장_시_존재하지_않는_문항이면_거부한다() {
        when(questions.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(user, 99L, new CoverLetterAnswerSaveRequest("내용", List.of(), null)))
                .isInstanceOf(CoverLetterException.class)
                .extracting(e -> ((CoverLetterException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 남의_경험을_근거로_쓰면_거부한다() {
        when(questions.findById(31L)).thenReturn(Optional.of(question));
        when(experiences.allOwnedBy(7L, List.of(999L))).thenReturn(false);

        assertThatThrownBy(() -> service.save(user, 31L,
                new CoverLetterAnswerSaveRequest("내용", List.of(999L), null)))
                .isInstanceOf(CoverLetterException.class)
                .extracting(e -> ((CoverLetterException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(answers, never()).save(any());
    }

    private static User user(Long id) {
        User user = User.firstLogin("T1", "U1", "지호", null, null);
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
