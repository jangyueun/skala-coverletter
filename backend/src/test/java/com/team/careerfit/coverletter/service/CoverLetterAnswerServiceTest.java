package com.team.careerfit.coverletter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.entity.CoverLetterAnswer;
import com.team.careerfit.coverletter.exception.CoverLetterException;
import com.team.careerfit.coverletter.repository.CoverLetterAnswerRepository;
import com.team.careerfit.job.entity.JobPostingQuestion;
import com.team.careerfit.job.repository.JobPostingQuestionRepository;
import com.team.careerfit.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoverLetterAnswerServiceTest {

    private final CoverLetterAnswerRepository answers = mock(CoverLetterAnswerRepository.class);
    private final JobPostingQuestionRepository questions = mock(JobPostingQuestionRepository.class);
    private final CoverLetterAnswerService service = new CoverLetterAnswerService(answers, questions);

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
        User user = user(7L);
        JobPostingQuestion question = mock(JobPostingQuestion.class);
        CoverLetterAnswer answer = CoverLetterAnswer.write(user, question, "제 강점은...", List.of(1L, 4L), null);
        when(answers.findByUserIdAndQuestionId(7L, 31L)).thenReturn(Optional.of(answer));

        CoverLetterAnswerResponse response = service.get(7L, 31L);

        assertThat(response.questionId()).isEqualTo(31L);
        assertThat(response.content()).isEqualTo("제 강점은...");
        assertThat(response.usedExperienceIds()).containsExactly(1L, 4L);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_문항이면_거부한다() {
        when(questions.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(7L, 99L)).isInstanceOf(CoverLetterException.class);
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
