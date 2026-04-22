package com.healthcare.domain.user.controller;

import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.user.dto.UpdateProfileRequest;
import com.healthcare.domain.user.dto.UserProfileResponse;
import com.healthcare.domain.user.service.UserService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        Long userId = resolveUserId(bearerToken);
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = resolveUserId(bearerToken);
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        Long userId = resolveUserId(bearerToken);
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ApiResponse.ok("계정이 삭제되었습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("유효하지 않은 인증 형식입니다.");
        }
        return jwtTokenProvider.getUserId(bearerToken.substring(7));
    }
}
