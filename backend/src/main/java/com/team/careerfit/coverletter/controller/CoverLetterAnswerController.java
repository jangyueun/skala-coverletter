package com.team.careerfit.coverletter.controller;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.dto.CoverLetterAnswerSaveRequest;
import com.team.careerfit.coverletter.service.CoverLetterAnswerService;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions/{questionId}/answer")
public class CoverLetterAnswerController {

    private final CurrentUser currentUser;
    private final CoverLetterAnswerService answerService;

    public CoverLetterAnswerController(CurrentUser currentUser, CoverLetterAnswerService answerService) {
        this.currentUser = currentUser;
        this.answerService = answerService;
    }

    @PutMapping
    public ResponseEntity<CoverLetterAnswerResponse> save(@PathVariable Long questionId,
            @Valid @RequestBody CoverLetterAnswerSaveRequest saveRequest, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(answerService.save(user, questionId, saveRequest));
    }
}
