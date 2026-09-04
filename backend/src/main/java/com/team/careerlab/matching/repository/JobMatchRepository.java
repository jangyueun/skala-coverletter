package com.team.careerlab.matching.repository;

import com.team.careerlab.matching.entity.JobMatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {

    /** (user, posting) 당 1행(uk_job_matches_user_posting). 재계산은 이 행을 덮어쓴다. */
    Optional<JobMatch> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);
}
