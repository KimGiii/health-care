package com.healthcare.security;

import com.healthcare.common.config.SecurityConfig;
import com.healthcare.domain.user.controller.UserController;
import com.healthcare.domain.user.dto.UserProfileResponse;
import com.healthcare.domain.user.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("JWT 보안 통합 테스트")
class JwtSecurityIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("인증 헤더 없이 보호된 엔드포인트 요청 시 401 JSON 반환")
    void protectedEndpoint_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verify(userService, never()).getProfile(USER_ID);
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 요청 시 401 JSON 반환")
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        given(jwtTokenProvider.validateToken(VALID_TOKEN)).willReturn(false);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(customUserDetailsService, never()).loadUserById(USER_ID);
        verify(userService, never()).getProfile(USER_ID);
    }

    @Test
    @DisplayName("만료된 토큰으로 요청 시 401 JSON 반환")
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        given(jwtTokenProvider.validateToken(EXPIRED_TOKEN)).willReturn(false);
        given(jwtTokenProvider.getUserId(EXPIRED_TOKEN))
                .willThrow(new ExpiredJwtException(null, null, "JWT expired"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(customUserDetailsService, never()).loadUserById(USER_ID);
        verify(userService, never()).getProfile(USER_ID);
    }

    @Test
    @DisplayName("유효한 토큰으로 요청 시 보안 필터를 통과하고 프로필을 반환한다")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        UserDetails userDetails = User.withUsername(String.valueOf(USER_ID))
                .password("encoded-password")
                .authorities("ROLE_USER")
                .build();

        given(jwtTokenProvider.validateToken(VALID_TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(VALID_TOKEN)).willReturn(USER_ID);
        given(customUserDetailsService.loadUserById(USER_ID)).willReturn(userDetails);
        given(userService.getProfile(USER_ID)).willReturn(UserProfileResponse.builder()
                .id(USER_ID)
                .email("test@example.com")
                .displayName("테스터")
                .onboardingCompleted(true)
                .createdAt(OffsetDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));

        verify(customUserDetailsService).loadUserById(USER_ID);
        verify(userService).getProfile(USER_ID);
    }
}
