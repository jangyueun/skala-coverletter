package com.team.careerlab.job.repository;

import com.team.careerlab.job.entity.JobPostingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingQuestionRepository extends JpaRepository<JobPostingQuestion, Long> {
}
