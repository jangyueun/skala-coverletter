package com.team.careerfit.competency.controller;

import com.team.careerfit.competency.dto.CompetencyResponse;
import com.team.careerfit.competency.service.CompetencyService;
import com.team.careerfit.global.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/competencies")
public class CompetencyController {

    private final CurrentUser currentUser;
    private final CompetencyService competencies;

    public CompetencyController(CurrentUser currentUser, CompetencyService competencies) {
        this.currentUser = currentUser;
        this.competencies = competencies;
    }

    @GetMapping
    public ResponseEntity<List<CompetencyResponse>> findAll(
            @RequestParam(required = false) String category,
            HttpServletRequest request) {
        currentUser.require(request);
        return ResponseEntity.ok(competencies.findAll(category));
    }
}
