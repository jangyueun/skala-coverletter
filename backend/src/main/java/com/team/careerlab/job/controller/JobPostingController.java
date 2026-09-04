package com.team.careerlab.job.controller;

import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.job.dto.BookmarkRequest;
import com.team.careerlab.job.dto.BookmarkResponse;
import com.team.careerlab.job.dto.PostingDetailResponse;
import com.team.careerlab.job.dto.PostingListResponse;
import com.team.careerlab.job.dto.PostingMatchResponse;
import com.team.careerlab.job.dto.PostingQuestionResponse;
import com.team.careerlab.job.service.JobPostingService;
import com.team.careerlab.job.service.PostingBookmarkService;
import com.team.careerlab.job.service.PostingMatchService;
import com.team.careerlab.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/postings")
@Tag(name = "채용공고", description = "채용공고와 문항, 북마크, 역량 매칭 정보를 조회합니다.")
public class JobPostingController {

    private final CurrentUser currentUser;
    private final JobPostingService jobPostings;
    private final PostingBookmarkService postingBookmarks;
    private final PostingMatchService postingMatches;

    public JobPostingController(
            CurrentUser currentUser,
            JobPostingService jobPostings,
            PostingBookmarkService postingBookmarks,
            PostingMatchService postingMatches) {
        this.currentUser = currentUser;
        this.jobPostings = jobPostings;
        this.postingBookmarks = postingBookmarks;
        this.postingMatches = postingMatches;
    }

    @GetMapping
    @Operation(summary = "채용공고 목록 조회", description = "검색어·역량·북마크·정렬·페이지 조건으로 채용공고를 조회합니다.")
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
    @Operation(summary = "채용공고 상세 조회", description = "채용공고의 회사·직무·내용·요구 역량을 조회합니다.")
    public ResponseEntity<PostingDetailResponse> findDetail(
            @PathVariable Long postingId,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(jobPostings.findDetail(user.getId(), postingId));
    }

    @GetMapping("/{postingId}/questions")
    @Operation(summary = "채용공고 문항 조회", description = "채용공고에 등록된 자기소개서 문항을 조회합니다.")
    public ResponseEntity<List<PostingQuestionResponse>> findQuestions(
            @PathVariable Long postingId,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(jobPostings.findQuestions(user.getId(), postingId));
    }

    @GetMapping("/{postingId}/match")
    @Operation(summary = "공고 매칭 결과 조회", description = "내 경험과 채용공고 요구 역량의 매칭 결과를 조회하거나 매칭 작업을 요청합니다.")
    public ResponseEntity<PostingMatchResponse> findMatch(
            @PathVariable Long postingId,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(postingMatches.findOrRequest(user.getId(), postingId));
    }

    @PutMapping("/{postingId}/bookmark")
    @Operation(summary = "채용공고 북마크 변경", description = "채용공고의 북마크 상태를 변경합니다.")
    public ResponseEntity<BookmarkResponse> updateBookmark(
            @PathVariable Long postingId,
            @RequestBody BookmarkRequest body,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(postingBookmarks.update(user.getId(), postingId, body.bookmarked()));
    }
}
