package com.healthcare.domain.auth.service;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.exception.ValidationException;
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

        User.Sex sex = parseSex(request.getSex());
        User.ActivityLevel activityLevel = parseActivityLevel(request.getActivityLevel());

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .sex(sex)
            .dateOfBirth(request.getDateOfBirth())
            .heightCm(request.getHeightCm())
            .weightKg(request.getWeightKg())
            .activityLevel(activityLevel)
            .locale(normalizeLocale(request.getLocale()))
            .timezone(normalizeTimezone(request.getTimezone()))
            .onboardingCompleted(hasCompletedOnboardingProfile(request))
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
            .userId(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
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

    private User.Sex parseSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return null;
        }
        try {
            return User.Sex.valueOf(sex);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("유효하지 않은 성별입니다: " + sex);
        }
    }

    private User.ActivityLevel parseActivityLevel(String activityLevel) {
        if (activityLevel == null || activityLevel.isBlank()) {
            return null;
        }
        try {
            return User.ActivityLevel.valueOf(activityLevel);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("유효하지 않은 활동 수준입니다: " + activityLevel);
        }
    }

    private String normalizeLocale(String locale) {
        return locale == null || locale.isBlank() ? "ko-KR" : locale;
    }

    private String normalizeTimezone(String timezone) {
        return timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone;
    }

    private boolean hasCompletedOnboardingProfile(RegisterRequest request) {
        return request.getSex() != null
            && request.getDateOfBirth() != null
            && request.getHeightCm() != null
            && request.getWeightKg() != null
            && request.getActivityLevel() != null
            && request.getLocale() != null
            && request.getTimezone() != null;
    }
}
