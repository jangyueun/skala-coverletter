package com.team.careerfit.coverletter.controller;

import com.team.careerfit.coverletter.dto.CoverLetterAnswerResponse;
import com.team.careerfit.coverletter.service.CoverLetterAnswerService;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping
    public ResponseEntity<CoverLetterAnswerResponse> get(@PathVariable Long questionId, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(answerService.get(user.getId(), questionId));
    }
}
