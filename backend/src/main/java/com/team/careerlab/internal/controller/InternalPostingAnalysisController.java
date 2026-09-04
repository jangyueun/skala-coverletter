package com.team.careerlab.internal.controller;

import com.team.careerlab.internal.dto.AnalysisTaskResponse;
import com.team.careerlab.internal.service.PostingAnalysisService;
import com.team.careerlab.internal.service.PostingAnalysisService.EnqueueResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/postings")
public class InternalPostingAnalysisController {

    private final PostingAnalysisService postingAnalysis;

    public InternalPostingAnalysisController(PostingAnalysisService postingAnalysis) {
        this.postingAnalysis = postingAnalysis;
    }

    @PostMapping("/{postingId}/analysis")
    public ResponseEntity<AnalysisTaskResponse> enqueue(
            @PathVariable Long postingId,
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        EnqueueResult result = postingAnalysis.enqueue(postingId, token);
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new AnalysisTaskResponse(result.taskId()));
    }
}
