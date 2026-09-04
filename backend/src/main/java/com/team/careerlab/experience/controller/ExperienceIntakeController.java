package com.team.careerlab.experience.controller;

import com.team.careerlab.aitask.dto.AiTaskCreatedResponse;
import com.team.careerlab.experience.service.ExperienceIntakeService;
import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/experience-intakes")
@Tag(name = "경험 AI 인테이크", description = "GitHub 링크와 파일에서 자기소개서용 경험 후보를 추출합니다.")
public class ExperienceIntakeController {

    private final CurrentUser currentUser;
    private final ExperienceIntakeService intakeService;

    public ExperienceIntakeController(CurrentUser currentUser, ExperienceIntakeService intakeService) {
        this.currentUser = currentUser;
        this.intakeService = intakeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "경험 후보 추출 요청", description = "입력한 GitHub 링크 또는 PDF·MD·TXT 파일을 AI가 분석합니다. 즉시 결과가 오지 않고 taskId를 반환하므로 AI 작업 조회 API를 폴링해야 합니다.")
    public ResponseEntity<AiTaskCreatedResponse> intake(
            @RequestParam(value = "links", required = false) String links,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        ExperienceIntakeService.Result result = intakeService.intake(user.getId(), links,
                files == null ? List.of() : files);

        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new AiTaskCreatedResponse(result.taskId()));
    }
}
