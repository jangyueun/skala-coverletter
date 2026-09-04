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

@RestController
@RequestMapping("/api/experience-intakes")
public class ExperienceIntakeController {

    private final CurrentUser currentUser;
    private final ExperienceIntakeService intakeService;

    public ExperienceIntakeController(CurrentUser currentUser, ExperienceIntakeService intakeService) {
        this.currentUser = currentUser;
        this.intakeService = intakeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
