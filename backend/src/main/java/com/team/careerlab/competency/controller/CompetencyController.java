package com.team.careerlab.competency.controller;

import com.team.careerlab.competency.dto.CompetencyResponse;
import com.team.careerlab.competency.service.CompetencyService;
import com.team.careerlab.global.security.CurrentUser;
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
