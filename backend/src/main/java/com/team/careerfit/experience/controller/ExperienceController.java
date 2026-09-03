package com.team.careerfit.experience.controller;

import com.team.careerfit.experience.dto.ExperienceCreateRequest;
import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.service.ExperienceService;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiences")
public class ExperienceController {

    private final CurrentUser currentUser;
    private final ExperienceService experienceService;

    public ExperienceController(CurrentUser currentUser, ExperienceService experienceService) {
        this.currentUser = currentUser;
        this.experienceService = experienceService;
    }

    @PostMapping
    public ResponseEntity<ExperienceSaveResponse> register(@Valid @RequestBody ExperienceCreateRequest createRequest,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        ExperienceSaveResponse response = experienceService.register(user, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
