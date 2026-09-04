package com.team.careerlab.job.repository;

import com.team.careerlab.job.entity.JobPosting;
import com.team.careerlab.job.entity.PostingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findByStatus(PostingStatus status);
}
