package com.team.careerfit.experience.controller;

import com.team.careerfit.experience.dto.ExperienceResponse;
import com.team.careerfit.experience.service.ExperienceService;
import com.team.careerfit.global.security.CurrentUser;
import com.team.careerfit.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public ResponseEntity<List<ExperienceResponse>> list(
            @RequestParam(required = false) Long competencyId, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(experienceService.list(user.getId(), competencyId));
    }
}
