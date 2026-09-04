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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/internal/postings")
@Tag(name = "내부 채용공고 분석", description = "내부 수집기가 호출하는 채용공고 AI 분석 API입니다.")
public class InternalPostingAnalysisController {

    private final PostingAnalysisService postingAnalysis;

    public InternalPostingAnalysisController(PostingAnalysisService postingAnalysis) {
        this.postingAnalysis = postingAnalysis;
    }

    @PostMapping("/{postingId}/analysis")
    @Operation(summary = "채용공고 분석 요청", description = "내부 토큰을 검증한 뒤 채용공고 역량 분석 작업을 등록합니다.")
    public ResponseEntity<AnalysisTaskResponse> enqueue(
            @PathVariable Long postingId,
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        EnqueueResult result = postingAnalysis.enqueue(postingId, token);
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new AnalysisTaskResponse(result.taskId()));
    }
}
