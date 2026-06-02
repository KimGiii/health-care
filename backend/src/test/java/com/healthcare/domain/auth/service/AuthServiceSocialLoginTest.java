package com.healthcare.domain.auth.service;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.auth.dto.SocialLoginRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.entity.RefreshToken;
import com.healthcare.domain.auth.repository.RefreshTokenRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.entity.UserIdentity;
import com.healthcare.domain.user.repository.UserIdentityRepository;
import com.healthcare.domain.user.repository.UserRepository;
import com.healthcare.security.JwtTokenProvider;
import com.healthcare.security.oauth.AppleIdTokenVerifier;
import com.healthcare.security.oauth.GoogleIdTokenVerifier;
import com.healthcare.security.oauth.OAuthUserInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService.socialLogin 단위 테스트")
class AuthServiceSocialLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AppleIdTokenVerifier appleIdTokenVerifier;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MeterRegistry registry = new SimpleMeterRegistry();
        authService = new AuthService(
                userRepository,
                userIdentityRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                appleIdTokenVerifier,
                googleIdTokenVerifier,
                registry);
    }

    @Test
    @DisplayName("기존 UserIdentity 가 있으면 그 사용자로 토큰을 발급한다 (Google 재로그인)")
    void socialLogin_existingIdentity_reusesUser() {
        OAuthUserInfo info = new OAuthUserInfo("google-sub-1", "g@example.com", true, "지수");
        given(googleIdTokenVerifier.verify("idtoken")).willReturn(info);

        User user = userWithId(10L, "g@example.com", true);
        UserIdentity identity = UserIdentity.builder()
                .user(user)
                .provider(UserIdentity.Provider.GOOGLE)
                .providerSubject("google-sub-1")
                .build();
        given(userIdentityRepository.findByProviderAndProviderSubject(
                UserIdentity.Provider.GOOGLE, "google-sub-1"))
                .willReturn(Optional.of(identity));
        given(userRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(10L, "g@example.com")).willReturn("a");
        given(jwtTokenProvider.generateRefreshToken(10L, "g@example.com")).willReturn("r");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        TokenResponse res = authService.socialLogin("GOOGLE", new SocialLoginRequest("idtoken"));

        assertThat(res.getUserId()).isEqualTo(10L);
        assertThat(res.getAccessToken()).isEqualTo("a");
        verify(userRepository, never()).save(any(User.class));
        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        verify(refreshTokenRepository).revokeAllByUserId(10L);
    }

    @Test
    @DisplayName("동일 이메일의 LOCAL 계정이 있고 email_verified=true 면 자동 연결한다")
    void socialLogin_emailVerifiedMatchesExisting_linksIdentity() {
        OAuthUserInfo info = new OAuthUserInfo("apple-sub-1", "u@example.com", true, null);
        given(appleIdTokenVerifier.verify("idtoken")).willReturn(info);
        given(userIdentityRepository.findByProviderAndProviderSubject(
                UserIdentity.Provider.APPLE, "apple-sub-1")).willReturn(Optional.empty());

        User existing = userWithId(20L, "u@example.com", true);
        given(userRepository.findByEmailAndDeletedAtIsNull("u@example.com"))
                .willReturn(Optional.of(existing));
        given(jwtTokenProvider.generateAccessToken(20L, "u@example.com")).willReturn("a");
        given(jwtTokenProvider.generateRefreshToken(20L, "u@example.com")).willReturn("r");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        TokenResponse res = authService.socialLogin("apple", new SocialLoginRequest("idtoken"));

        assertThat(res.getUserId()).isEqualTo(20L);
        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUser()).isSameAs(existing);
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(UserIdentity.Provider.APPLE);
        assertThat(identityCaptor.getValue().getProviderSubject()).isEqualTo("apple-sub-1");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("email_verified=false 면 동일 이메일이 있어도 신규 계정으로 만든다")
    void socialLogin_emailNotVerified_createsNewUser() {
        OAuthUserInfo info = new OAuthUserInfo("apple-sub-2", "u@example.com", false, null);
        given(appleIdTokenVerifier.verify("idtoken")).willReturn(info);
        given(userIdentityRepository.findByProviderAndProviderSubject(
                UserIdentity.Provider.APPLE, "apple-sub-2")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            setField(u, "id", 30L);
            return u;
        });
        given(jwtTokenProvider.generateAccessToken(30L, "u@example.com")).willReturn("a");
        given(jwtTokenProvider.generateRefreshToken(30L, "u@example.com")).willReturn("r");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        TokenResponse res = authService.socialLogin("APPLE", new SocialLoginRequest("idtoken"));

        assertThat(res.isOnboardingCompleted()).isFalse();
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        assertThat(userCaptor.getValue().isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("신규 Google 가입 — email 이 없는 토큰도 합성 이메일로 생성된다")
    void socialLogin_newUserWithoutEmail_synthesizesPlaceholder() {
        OAuthUserInfo info = new OAuthUserInfo("google-sub-2", null, false, "테스터");
        given(googleIdTokenVerifier.verify("idtoken")).willReturn(info);
        given(userIdentityRepository.findByProviderAndProviderSubject(
                UserIdentity.Provider.GOOGLE, "google-sub-2")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            setField(u, "id", 40L);
            return u;
        });
        given(jwtTokenProvider.generateAccessToken(any(), any())).willReturn("a");
        given(jwtTokenProvider.generateRefreshToken(any(), any())).willReturn("r");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        authService.socialLogin("GOOGLE", new SocialLoginRequest("idtoken"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("google_google-sub-2@social.local");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("identity 는 있지만 user 가 soft-delete 됐다면 고아 identity 를 삭제하고 신규 가입한다")
    void socialLogin_orphanedIdentity_deletesAndCreatesNew() {
        OAuthUserInfo info = new OAuthUserInfo("apple-sub-orphan", "orphan@example.com", true, null);
        given(appleIdTokenVerifier.verify("idtoken")).willReturn(info);

        User deletedUser = userWithId(99L, "orphan@example.com", true);
        UserIdentity orphan = UserIdentity.builder()
                .user(deletedUser)
                .provider(UserIdentity.Provider.APPLE)
                .providerSubject("apple-sub-orphan")
                .build();
        given(userIdentityRepository.findByProviderAndProviderSubject(
                UserIdentity.Provider.APPLE, "apple-sub-orphan"))
                .willReturn(Optional.of(orphan));
        given(userRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());
        // 자동 연결도 막혀야 함 — soft-deleted 라 findByEmailAndDeletedAtIsNull 도 비어있음
        given(userRepository.findByEmailAndDeletedAtIsNull("orphan@example.com"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            setField(u, "id", 100L);
            return u;
        });
        given(jwtTokenProvider.generateAccessToken(any(), any())).willReturn("a");
        given(jwtTokenProvider.generateRefreshToken(any(), any())).willReturn("r");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        TokenResponse res = authService.socialLogin("APPLE", new SocialLoginRequest("idtoken"));

        verify(userIdentityRepository).delete(orphan);
        verify(userIdentityRepository).flush();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("orphan@example.com");
        assertThat(res.getUserId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("지원하지 않는 provider 는 ValidationException 으로 차단된다")
    void socialLogin_unsupportedProvider_throws() {
        assertThatThrownBy(() -> authService.socialLogin("kakao", new SocialLoginRequest("t")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("지원하지 않는");

        assertThatThrownBy(() -> authService.socialLogin(null, new SocialLoginRequest("t")))
                .isInstanceOf(ValidationException.class);
    }

    // ---- helpers ----

    private static User userWithId(Long id, String email, boolean onboardingCompleted) {
        User user = User.builder()
                .email(email)
                .displayName("name")
                .locale("ko-KR")
                .timezone("Asia/Seoul")
                .onboardingCompleted(onboardingCompleted)
                .build();
        setField(user, "id", id);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
