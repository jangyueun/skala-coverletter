package com.team.careerfit.job.controller;

import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.job.dto.PostingDetailResponse;
import com.team.careerfit.job.dto.PostingListResponse;
import com.team.careerfit.job.service.JobPostingService;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/postings")
public class JobPostingController {

    private final CurrentUser currentUser;
    private final JobPostingService jobPostings;

    public JobPostingController(CurrentUser currentUser, JobPostingService jobPostings) {
        this.currentUser = currentUser;
        this.jobPostings = jobPostings;
    }

    @GetMapping
    public ResponseEntity<PostingListResponse> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(name = "competencyId", required = false) List<Long> competencyIds,
            @RequestParam(required = false) Boolean bookmarked,
            @RequestParam(defaultValue = "match") String sort,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        PostingListResponse response = jobPostings.findAll(
                user.getId(),
                q,
                competencyIds == null ? List.of() : competencyIds,
                bookmarked,
                sort,
                includeClosed,
                page,
                size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{postingId}")
    public ResponseEntity<PostingDetailResponse> findDetail(
            @PathVariable Long postingId,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(jobPostings.findDetail(user.getId(), postingId));
    }
}
