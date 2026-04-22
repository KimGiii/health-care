package com.healthcare.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.service.AuthService;
import com.healthcare.security.JwtTokenProvider;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 컨트롤러 단위 테스트.
 *
 * standaloneSetup 방식으로 Spring Security 필터 없이 컨트롤러 로직과
 * 예외 처리(GlobalExceptionHandler)에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 단위 테스트")
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;
    private static final String BEARER = "Bearer valid.test.token";
    private static final String TOKEN  = "valid.test.token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        lenient().when(jwtTokenProvider.getUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ─────────────────────────── POST /register ───────────────────────────

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

    // ─────────────────────────── POST /login ───────────────────────────

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

    // ─────────────────────────── POST /token/refresh ───────────────────────────

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

    // ─────────────────────────── POST /logout ───────────────────────────

    @Test
    @DisplayName("로그아웃 성공 — 200 반환")
    void logout_success_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(USER_ID);
    }

    @Test
    @DisplayName("Authorization 헤더 없이 로그아웃 — 401 반환")
    void logout_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰으로 로그아웃 — 401 반환")
    void logout_invalidBearerFormat_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

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
