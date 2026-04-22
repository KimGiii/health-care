package com.healthcare.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.domain.user.dto.UpdateProfileRequest;
import com.healthcare.domain.user.dto.UserProfileResponse;
import com.healthcare.domain.user.service.UserService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 컨트롤러 단위 테스트.
 *
 * standaloneSetup 방식으로 Spring Security 필터 없이 컨트롤러 로직과
 * 예외 처리(GlobalExceptionHandler)에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserController userController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        lenient().when(jwtTokenProvider.getUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ─────────────────────────── GET /me ───────────────────────────

    @Test
    @DisplayName("프로필 조회 성공 — 200, 사용자 정보 포함")
    void getMyProfile_success_returns200() throws Exception {
        given(userService.getProfile(USER_ID)).willReturn(buildProfileResponse());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("테스터"))
                .andExpect(jsonPath("$.data.heightCm").value(175.0))
                .andExpect(jsonPath("$.data.weightKg").value(70.0));

        verify(userService).getProfile(USER_ID);
    }

    @Test
    @DisplayName("Authorization 헤더 없이 프로필 조회 — 401 반환")
    void getMyProfile_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰으로 프로필 조회 — 401 반환")
    void getMyProfile_invalidBearerFormat_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "token-without-bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─────────────────────────── PATCH /me ───────────────────────────

    @Test
    @DisplayName("프로필 수정 성공 — 200, 변경된 displayName 반환")
    void updateMyProfile_success_returns200() throws Exception {
        UserProfileResponse updated = UserProfileResponse.builder()
                .id(USER_ID)
                .email("test@example.com")
                .displayName("새닉네임")
                .heightCm(175.0)
                .weightKg(68.0)
                .onboardingCompleted(true)
                .createdAt(OffsetDateTime.now())
                .build();

        given(userService.updateProfile(eq(USER_ID), any(UpdateProfileRequest.class))).willReturn(updated);

        String body = """
                {"displayName":"새닉네임","weightKg":68.0}
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("새닉네임"))
                .andExpect(jsonPath("$.data.weightKg").value(68.0));

        verify(userService).updateProfile(eq(USER_ID), any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("닉네임 101자 초과 — 400 + fieldErrors.displayName 포함")
    void updateMyProfile_displayNameTooLong_returns400() throws Exception {
        String tooLong = "A".repeat(101);
        String body = "{\"displayName\":\"" + tooLong + "\"}";

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists());
    }

    @Test
    @DisplayName("체중 20kg 미만 — 400 + fieldErrors.weightKg 포함")
    void updateMyProfile_weightTooLow_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":10.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='weightKg')]").exists());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 프로필 수정 — 401 반환")
    void updateMyProfile_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"새닉네임\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─────────────────────────── DELETE /me ───────────────────────────

    @Test
    @DisplayName("계정 삭제 성공 — 200 반환")
    void deleteMyAccount_success_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).deleteAccount(USER_ID);
    }

    @Test
    @DisplayName("Authorization 헤더 없이 계정 삭제 — 401 반환")
    void deleteMyAccount_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private UserProfileResponse buildProfileResponse() {
        return UserProfileResponse.builder()
                .id(USER_ID)
                .email("test@example.com")
                .displayName("테스터")
                .sex("MALE")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .heightCm(175.0)
                .weightKg(70.0)
                .activityLevel("MODERATE")
                .locale("ko")
                .timezone("Asia/Seoul")
                .calorieTarget(2000)
                .proteinTargetG(150)
                .carbTargetG(250)
                .fatTargetG(70)
                .onboardingCompleted(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
