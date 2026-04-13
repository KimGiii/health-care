package com.healthcare.domain.auth.service;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.entity.RefreshToken;
import com.healthcare.domain.auth.repository.RefreshTokenRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import com.healthcare.security.JwtTokenProvider;
import com.healthcare.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .build();

        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        refreshTokenRepository.revokeAllByUserId(user.getId());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hash(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 리프레시 토큰입니다."));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("만료된 리프레시 토큰입니다.");
        }

        refreshToken.revoke();

        User user = userRepository.findByIdAndDeletedAtIsNull(refreshToken.getUserId())
            .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshTokenRaw = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(hash(refreshTokenRaw))
            .expiresAt(OffsetDateTime.now().plusDays(30))
            .build();

        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshTokenRaw)
            .expiresIn(SecurityConstants.ACCESS_TOKEN_EXPIRY_MS / 1000)
            .onboardingCompleted(user.isOnboardingCompleted())
            .build();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
