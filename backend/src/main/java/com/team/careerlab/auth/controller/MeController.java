package com.team.careerlab.auth.controller;

import com.team.careerlab.global.security.CurrentUser;
import com.team.careerlab.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 로그인 상태 확인.
 *
 * <p>프론트는 앱을 켤 때 이걸 한 번 호출해 로그인 여부를 정한다.
 * 로그인 안 됐으면 <b>401 이 아니라 200 + null</b> 을 준다 — 로그인 안 된 게 정상 상태인
 * 화면(랜딩)에서 콘솔에 빨간 401 이 찍히는 것을 피한다.
 */
@RestController
@Tag(name = "인증", description = "로그인 상태를 확인하고 Slack 로그인을 처리합니다.")
public class MeController {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/auth/me")
    @Operation(summary = "로그인 상태 조회", description = "로그인되어 있으면 사용자 정보를, 로그아웃 상태면 null을 반환합니다.")
    public ResponseEntity<UserResponse> me(HttpServletRequest request) {
        return ResponseEntity.ok(currentUser.find(request).map(UserResponse::from).orElse(null));
    }
}
