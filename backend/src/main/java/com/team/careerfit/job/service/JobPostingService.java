package com.team.careerfit.job.service;

import com.team.careerfit.job.entity.JobPosting;
import com.team.careerfit.job.entity.PostingStatus;
import com.team.careerfit.job.repository.JobPostingRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostings;

    public JobPostingService(JobPostingRepository jobPostings) {
        this.jobPostings = jobPostings;
    }

    /** 경험을 저장·수정할 때마다 활성 공고 전부를 다시 매칭시키기 위해 쓴다. */
    @Transactional(readOnly = true)
    public List<Long> findActivePostingIds() {
        return jobPostings.findByStatus(PostingStatus.ACTIVE).stream().map(JobPosting::getId).toList();
    }
}
