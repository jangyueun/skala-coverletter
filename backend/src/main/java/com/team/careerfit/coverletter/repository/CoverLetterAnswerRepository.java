package com.team.careerfit.coverletter.repository;

import com.team.careerfit.coverletter.entity.CoverLetterAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterAnswerRepository extends JpaRepository<CoverLetterAnswer, Long> {

    Optional<CoverLetterAnswer> findByUserIdAndQuestionId(Long userId, Long questionId);
}
