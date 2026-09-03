package com.team.careerfit.experience.controller;

import com.team.careerfit.experience.dto.ExperienceSaveResponse;
import com.team.careerfit.experience.dto.ExperienceUpdateRequest;
import com.team.careerfit.experience.service.ExperienceService;
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
@RequestMapping("/api/experiences")
public class ExperienceController {

    private final CurrentUser currentUser;
    private final ExperienceService experienceService;

    public ExperienceController(CurrentUser currentUser, ExperienceService experienceService) {
        this.currentUser = currentUser;
        this.experienceService = experienceService;
    }

    @PutMapping("/{experienceId}")
    public ResponseEntity<ExperienceSaveResponse> update(@PathVariable Long experienceId,
            @Valid @RequestBody ExperienceUpdateRequest updateRequest, HttpServletRequest request) {
        User user = currentUser.require(request);
        ExperienceSaveResponse response = experienceService.update(user, experienceId, updateRequest);
        return ResponseEntity.ok(response);
    }
}
