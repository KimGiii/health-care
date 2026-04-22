package com.healthcare.domain.auth.controller;

import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.service.AuthService;
import com.healthcare.security.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        Long userId = resolveUserId(bearerToken);
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("유효하지 않은 인증 형식입니다.");
        }
        return jwtTokenProvider.getUserId(bearerToken.substring(7));
    }
}
