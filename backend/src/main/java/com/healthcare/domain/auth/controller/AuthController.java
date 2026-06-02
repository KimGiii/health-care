package com.healthcare.domain.auth.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.SocialLoginCheckResponse;
import com.healthcare.domain.auth.dto.SocialLoginCommitRequest;
import com.healthcare.domain.auth.dto.SocialLoginRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.service.AuthService;
import com.healthcare.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/social-login/{provider}")
    @Deprecated
    public ResponseEntity<ApiResponse<TokenResponse>> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.socialLogin(provider, request)));
    }

    @PostMapping("/social-login/{provider}/check")
    public ResponseEntity<ApiResponse<SocialLoginCheckResponse>> socialLoginCheck(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.socialLoginCheck(provider, request)));
    }

    @PostMapping("/social-login/{provider}/commit")
    public ResponseEntity<ApiResponse<TokenResponse>> socialLoginCommit(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginCommitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.socialLoginCommit(provider, request)));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CurrentUserId Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }
}
