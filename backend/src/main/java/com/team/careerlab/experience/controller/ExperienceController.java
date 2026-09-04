package com.team.careerlab.experience.controller;

import com.team.careerlab.experience.dto.ExperienceCreateRequest;
import com.team.careerlab.experience.dto.ExperienceResponse;
import com.team.careerlab.experience.dto.ExperienceSaveResponse;
import com.team.careerlab.experience.dto.ExperienceUpdateRequest;
import com.team.careerlab.experience.service.ExperienceService;
import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/experiences")
@Tag(name = "경험", description = "사용자의 경험을 조회·등록·수정합니다.")
public class ExperienceController {

    private final CurrentUser currentUser;
    private final ExperienceService experienceService;

    public ExperienceController(CurrentUser currentUser, ExperienceService experienceService) {
        this.currentUser = currentUser;
        this.experienceService = experienceService;
    }

    @GetMapping
    @Operation(summary = "내 경험 목록 조회", description = "로그인한 사용자의 경험을 조회합니다. competencyId를 지정하면 해당 역량이 연결된 경험만 반환합니다.")
    public ResponseEntity<List<ExperienceResponse>> list(
            @RequestParam(required = false) Long competencyId, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(experienceService.list(user.getId(), competencyId));
    }

    @PostMapping
    @Operation(summary = "경험 등록", description = "새 경험을 등록하고 선택한 역량을 연결합니다.")
    public ResponseEntity<ExperienceSaveResponse> register(@Valid @RequestBody ExperienceCreateRequest createRequest,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        ExperienceSaveResponse response = experienceService.register(user, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{experienceId}")
    @Operation(summary = "경험 수정", description = "로그인한 사용자가 소유한 경험을 수정합니다.")
    public ResponseEntity<ExperienceSaveResponse> update(@PathVariable Long experienceId,
            @Valid @RequestBody ExperienceUpdateRequest updateRequest, HttpServletRequest request) {
        User user = currentUser.require(request);
        ExperienceSaveResponse response = experienceService.update(user, experienceId, updateRequest);
        return ResponseEntity.ok(response);
    }
}
