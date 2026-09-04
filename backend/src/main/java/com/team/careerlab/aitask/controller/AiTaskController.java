package com.team.careerlab.aitask.controller;

import com.team.careerlab.aitask.dto.AiTaskListResponse;
import com.team.careerlab.aitask.dto.AiTaskResponse;
import com.team.careerlab.aitask.entity.AiTaskStatus;
import com.team.careerlab.aitask.entity.AiTaskType;
import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 작업 폴링(docs/api-spec-v6.md §6). 프론트는 초안·인테이크를 요청해 202 + taskId 를 받은 뒤
 * {@code GET /api/ai-tasks/{taskId}} 를 1초 간격으로 부른다(api/real/ai.js waitFor).
 */
@RestController
@RequestMapping("/api/ai-tasks")
public class AiTaskController {

    private final CurrentUser currentUser;
    private final AiTaskService aiTasks;

    public AiTaskController(CurrentUser currentUser, AiTaskService aiTasks) {
        this.currentUser = currentUser;
        this.aiTasks = aiTasks;
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AiTaskResponse> find(@PathVariable Long taskId, HttpServletRequest request) {
        User user = currentUser.require(request);
        return ResponseEntity.ok(aiTasks.find(user.getId(), taskId));
    }

    @GetMapping
    public ResponseEntity<AiTaskListResponse> list(
            @RequestParam(required = false) AiTaskType type,
            @RequestParam(name = "status", required = false) List<AiTaskStatus> statuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            HttpServletRequest request) {
        User user = currentUser.require(request);
        Set<AiTaskStatus> statusSet = statuses == null ? Set.of() : new HashSet<>(statuses);
        return ResponseEntity.ok(aiTasks.list(user.getId(), type, statusSet, since));
    }
}
