package com.team.careerlab.coverletter.controller;

import com.team.careerlab.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerlab.coverletter.dto.CoverLetterAnswerSaveRequest;
import com.team.careerlab.coverletter.service.CoverLetterAnswerService;
import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/questions/{questionId}/answer")
@Tag(name = "자기소개서 답변", description = "자기소개서 답변을 조회하고 저장합니다.")
public class CoverLetterAnswerController {

    private final CurrentUser currentUser;
    private final CoverLetterAnswerService answerService;

    public CoverLetterAnswerController(CurrentUser currentUser, CoverLetterAnswerService answerService) {
        this.currentUser = currentUser;
        this.answerService = answerService;
    }

    @GetMapping
    @Operation(summary = "자기소개서 답변 조회", description = "문항에 저장된 답변과 사용 경험 목록을 조회합니다.")
    public ResponseEntity<CoverLetterAnswerResponse> get(@PathVariable Long questionId, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(answerService.get(user.getId(), questionId));
    }

    @PutMapping
    @Operation(summary = "자기소개서 답변 저장", description = "작성한 답변과 사용 경험을 저장합니다. AI 초안에서 시작한 경우 draftTaskId로 출처를 남길 수 있습니다.")
    public ResponseEntity<CoverLetterAnswerResponse> save(@PathVariable Long questionId,
            @Valid @RequestBody CoverLetterAnswerSaveRequest saveRequest, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(answerService.save(user, questionId, saveRequest));
    }
}
