package com.team.careerfit.coverletter.service;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.exception.CoverLetterException;
import com.team.careerfit.coverletter.repository.CoverLetterAnswerRepository;
import com.team.careerfit.job.repository.JobPostingQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoverLetterAnswerService {

    private final CoverLetterAnswerRepository answers;
    private final JobPostingQuestionRepository questions;

    public CoverLetterAnswerService(CoverLetterAnswerRepository answers, JobPostingQuestionRepository questions) {
        this.answers = answers;
        this.questions = questions;
    }

    /** 답변을 저장한 적 없으면 빈 값(빈 문자열·빈 배열·updatedAt null)을 돌려준다. */
    @Transactional(readOnly = true)
    public CoverLetterAnswerResponse get(Long userId, Long questionId) {
        if (!questions.existsById(questionId)) {
            throw CoverLetterException.questionNotFound();
        }
        return answers.findByUserIdAndQuestionId(userId, questionId)
                .map(answer -> CoverLetterAnswerResponse.from(questionId, answer))
                .orElseGet(() -> CoverLetterAnswerResponse.empty(questionId));
    }
}
