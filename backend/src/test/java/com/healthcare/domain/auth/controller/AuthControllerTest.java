package com.healthcare.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.SocialLoginRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.service.AuthService;
import org.mockito.ArgumentMatchers;
import com.healthcare.security.CurrentUserIdArgumentResolver;
import com.healthcare.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 단위 테스트")
class AuthControllerTest {

    @Mock private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .build();

        SecurityTestSupport.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clear();
    }

    @Test
    @DisplayName("회원가입 성공 — 201, accessToken/refreshToken 포함")
    void register_success_returns201() throws Exception {
        given(authService.register(any(RegisterRequest.class))).willReturn(buildTokenResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("이메일 누락 시 400 + INVALID_INPUT 반환")
    void register_missingEmail_returns400() throws Exception {
        String body = """
                {"password":"password123","displayName":"테스터"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("잘못된 이메일 형식 — 400 + fieldErrors.email 포함")
    void register_invalidEmail_returns400WithFieldError() throws Exception {
        String body = """
                {"email":"not-an-email","password":"password123","displayName":"테스터"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists());
    }

    @Test
    @DisplayName("비밀번호 8자 미만 — 400 + fieldErrors.password 포함")
    void register_shortPassword_returns400WithFieldError() throws Exception {
        String body = """
                {"email":"test@example.com","password":"short","displayName":"테스터"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    @DisplayName("중복 이메일 — 409 CONFLICT 반환")
    void register_duplicateEmail_returns409() throws Exception {
        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new DuplicateResourceException("이미 사용 중인 이메일입니다."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("로그인 성공 — 200, accessToken 포함")
    void login_success_returns200() throws Exception {
        given(authService.login(any(LoginRequest.class))).willReturn(buildTokenResponse());

        String body = """
                {"email":"test@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("잘못된 인증정보 로그인 — 401 반환")
    void login_invalidCredentials_returns401() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        String body = """
                {"email":"test@example.com","password":"wrongpassword"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("이메일 누락 로그인 — 400 반환")
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("토큰 갱신 성공 — 200, 새 accessToken 포함")
    void refreshToken_success_returns200() throws Exception {
        given(authService.refreshToken(any(RefreshTokenRequest.class))).willReturn(buildTokenResponse());

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid.refresh.token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access.token"));

        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    @DisplayName("리프레시 토큰 누락 — 400 반환")
    void refreshToken_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("만료된 리프레시 토큰 — 401 반환")
    void refreshToken_expired_returns401() throws Exception {
        given(authService.refreshToken(any(RefreshTokenRequest.class)))
                .willThrow(new UnauthorizedException("리프레시 토큰이 만료되었습니다."));

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired.token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그아웃 성공 — 200 반환")
    void logout_success_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(USER_ID);
    }

    @Test
    @DisplayName("인증되지 않은 로그아웃 요청 — 401 반환")
    void logout_unauthenticated_returns401() throws Exception {
        SecurityTestSupport.clear();

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("소셜로그인 성공 — 200, 토큰 반환 (Apple)")
    void socialLogin_apple_success_returns200() throws Exception {
        given(authService.socialLogin(ArgumentMatchers.eq("APPLE"), any(SocialLoginRequest.class)))
                .willReturn(buildTokenResponse());

        mockMvc.perform(post("/api/v1/auth/social-login/APPLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"id.tok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"));

        verify(authService).socialLogin(ArgumentMatchers.eq("APPLE"), any(SocialLoginRequest.class));
    }

    @Test
    @DisplayName("소셜로그인 — idToken 누락이면 400")
    void socialLogin_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social-login/GOOGLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("소셜로그인 — 지원하지 않는 provider 면 ValidationException → 400")
    void socialLogin_unsupportedProvider_returns400() throws Exception {
        given(authService.socialLogin(ArgumentMatchers.eq("KAKAO"), any(SocialLoginRequest.class)))
                .willThrow(new ValidationException("지원하지 않는 provider 입니다: KAKAO"));

        mockMvc.perform(post("/api/v1/auth/social-login/KAKAO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"id.tok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("소셜로그인 — 유효하지 않은 ID 토큰이면 401")
    void socialLogin_invalidIdToken_returns401() throws Exception {
        given(authService.socialLogin(ArgumentMatchers.eq("GOOGLE"), any(SocialLoginRequest.class)))
                .willThrow(new UnauthorizedException("유효하지 않은 ID 토큰입니다."));

        mockMvc.perform(post("/api/v1/auth/social-login/GOOGLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    private TokenResponse buildTokenResponse() {
        return TokenResponse.builder()
                .userId(USER_ID)
                .email("test@example.com")
                .displayName("테스터")
                .accessToken("access.token")
                .refreshToken("refresh.token")
                .expiresIn(3600L)
                .onboardingCompleted(true)
                .build();
    }

    private String validRegisterJson() {
        return """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "displayName": "테스터"
                }
                """;
    }
}
