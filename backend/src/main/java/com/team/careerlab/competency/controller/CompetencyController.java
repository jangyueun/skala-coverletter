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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/competencies")
@Tag(name = "역량", description = "매칭과 경험 등록에 사용하는 역량 사전을 조회합니다.")
public class CompetencyController {

    private final CurrentUser currentUser;
    private final CompetencyService competencies;

    public CompetencyController(CurrentUser currentUser, CompetencyService competencies) {
        this.currentUser = currentUser;
        this.competencies = competencies;
    }

    @GetMapping
    @Operation(summary = "역량 사전 조회", description = "역량 카테고리를 지정해 필터링할 수 있습니다.")
    public ResponseEntity<List<CompetencyResponse>> findAll(
            @RequestParam(required = false) String category,
            HttpServletRequest request) {
        currentUser.require(request);
        return ResponseEntity.ok(competencies.findAll(category));
    }
}
