package com.healthcare.domain.auth.service;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.entity.RefreshToken;
import com.healthcare.domain.auth.repository.RefreshTokenRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import com.healthcare.security.JwtTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 시 초기 프로필 필드와 지역 설정을 저장한다")
    void register_withProfileFields_savesExtendedProfile() {
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "minjun@example.com");
        setField(request, "password", "SecurePassword123!");
        setField(request, "displayName", "민준");
        setField(request, "sex", "MALE");
        setField(request, "dateOfBirth", LocalDate.of(1997, 3, 15));
        setField(request, "heightCm", 176.0);
        setField(request, "weightKg", 82.0);
        setField(request, "activityLevel", "MODERATELY_ACTIVE");
        setField(request, "locale", "ko-KR");
        setField(request, "timezone", "Asia/Seoul");

        given(userRepository.existsByEmailAndDeletedAtIsNull("minjun@example.com")).willReturn(false);
        given(passwordEncoder.encode("SecurePassword123!")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setField(user, "id", 1L);
            return user;
        });
        given(jwtTokenProvider.generateAccessToken(1L, "minjun@example.com")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(1L, "minjun@example.com")).willReturn("refresh-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getSex()).isEqualTo(User.Sex.MALE);
        assertThat(savedUser.getDateOfBirth()).isEqualTo(LocalDate.of(1997, 3, 15));
        assertThat(savedUser.getHeightCm()).isEqualTo(176.0);
        assertThat(savedUser.getWeightKg()).isEqualTo(82.0);
        assertThat(savedUser.getActivityLevel()).isEqualTo(User.ActivityLevel.MODERATELY_ACTIVE);
        assertThat(savedUser.getLocale()).isEqualTo("ko-KR");
        assertThat(savedUser.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(savedUser.isOnboardingCompleted()).isTrue();

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("minjun@example.com");
        assertThat(response.getDisplayName()).isEqualTo("민준");
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("회원가입 시 locale과 timezone이 없으면 기본값을 저장한다")
    void register_withoutLocaleAndTimezone_usesDefaults() {
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "basic@example.com");
        setField(request, "password", "SecurePassword123!");
        setField(request, "displayName", "기본 사용자");

        given(userRepository.existsByEmailAndDeletedAtIsNull("basic@example.com")).willReturn(false);
        given(passwordEncoder.encode("SecurePassword123!")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setField(user, "id", 2L);
            return user;
        });
        given(jwtTokenProvider.generateAccessToken(2L, "basic@example.com")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(2L, "basic@example.com")).willReturn("refresh-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLocale()).isEqualTo("ko-KR");
        assertThat(savedUser.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(savedUser.isOnboardingCompleted()).isFalse();
        assertThat(response.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("회원가입 시 유효하지 않은 활동 수준이면 ValidationException이 발생한다")
    void register_withInvalidActivityLevel_throwsValidationException() {
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "invalid@example.com");
        setField(request, "password", "SecurePassword123!");
        setField(request, "displayName", "잘못된 활동");
        setField(request, "activityLevel", "ATHLETE");

        given(userRepository.existsByEmailAndDeletedAtIsNull("invalid@example.com")).willReturn(false);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("유효하지 않은 활동 수준");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
