package com.team.careerfit.coverletter.controller;

import com.team.careerfit.aitask.dto.AiTaskCreatedResponse;
import com.team.careerfit.coverletter.dto.CoverLetterDraftRequest;
import com.team.careerfit.coverletter.service.CoverLetterDraftService;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions/{questionId}/drafts")
public class CoverLetterDraftController {

    private final CurrentUser currentUser;
    private final CoverLetterDraftService draftService;

    public CoverLetterDraftController(CurrentUser currentUser, CoverLetterDraftService draftService) {
        this.currentUser = currentUser;
        this.draftService = draftService;
    }

    @PostMapping
    public ResponseEntity<AiTaskCreatedResponse> requestDraft(@PathVariable Long questionId,
            @Valid @RequestBody CoverLetterDraftRequest draftRequest, HttpServletRequest request) {
        User user = currentUser.require(request);
        CoverLetterDraftService.Result result = draftService.requestDraft(user.getId(), questionId,
                draftRequest.experienceIds());

        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new AiTaskCreatedResponse(result.taskId()));
    }
}
