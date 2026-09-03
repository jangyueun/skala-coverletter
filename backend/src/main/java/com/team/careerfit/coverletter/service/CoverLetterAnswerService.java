package com.team.careerfit.coverletter.service;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.dto.CoverLetterAnswerSaveRequest;
import com.team.careerfit.coverletter.entity.CoverLetterAnswer;
import com.team.careerfit.coverletter.exception.CoverLetterException;
import com.team.careerfit.coverletter.repository.CoverLetterAnswerRepository;
import com.team.careerfit.experience.service.ExperienceService;
import com.team.careerfit.job.entity.JobPostingQuestion;
import com.team.careerfit.job.repository.JobPostingQuestionRepository;
import com.team.careerfit.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoverLetterAnswerService {

    private final CoverLetterAnswerRepository answers;
    private final JobPostingQuestionRepository questions;
    private final ExperienceService experiences;

    public CoverLetterAnswerService(CoverLetterAnswerRepository answers, JobPostingQuestionRepository questions,
            ExperienceService experiences) {
        this.answers = answers;
        this.questions = questions;
        this.experiences = experiences;
    }

    /**
     * 저장할 때마다 본문 전체를 덮어쓴다((user, question) 당 1행). 근거 경험은 반드시 이 사용자 것이어야 한다.
     *
     * @throws CoverLetterException 문항이 없으면 {@code QUESTION_NOT_FOUND}, 남의 경험을 근거로 쓰면 {@code FORBIDDEN}
     */
    @Transactional
    public CoverLetterAnswerResponse save(User user, Long questionId, CoverLetterAnswerSaveRequest request) {
        JobPostingQuestion question = questions.findById(questionId)
                .orElseThrow(CoverLetterException::questionNotFound);

        if (!experiences.allOwnedBy(user.getId(), request.usedExperienceIds())) {
            throw CoverLetterException.forbidden();
        }

        CoverLetterAnswer answer = answers.findByUserIdAndQuestionId(user.getId(), questionId)
                .map(existing -> {
                    existing.rewrite(request.content(), request.usedExperienceIds(), request.draftTaskId());
                    return existing;
                })
                .orElseGet(() -> answers.save(CoverLetterAnswer.write(user, question, request.content(),
                        request.usedExperienceIds(), request.draftTaskId())));

        return CoverLetterAnswerResponse.from(questionId, answer);
    }
}
