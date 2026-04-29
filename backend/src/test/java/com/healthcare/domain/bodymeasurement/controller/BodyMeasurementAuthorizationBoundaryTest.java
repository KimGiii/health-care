package com.healthcare.domain.bodymeasurement.controller;

import com.healthcare.common.config.SecurityConfig;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.service.BodyMeasurementService;
import com.healthcare.security.CustomUserDetailsService;
import com.healthcare.security.JwtAuthenticationFilter;
import com.healthcare.security.JwtTokenProvider;
import com.healthcare.security.RestAccessDeniedHandler;
import com.healthcare.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BodyMeasurementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("신체 측정 도메인 권한 경계 테스트")
class BodyMeasurementAuthorizationBoundaryTest {

    private static final Long ATTACKER_ID = 2L;
    private static final Long MEASUREMENT_ID = 100L;
    private static final String ATTACKER_TOKEN = "attacker.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BodyMeasurementService bodyMeasurementService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        UserDetails attackerDetails = User.withUsername(String.valueOf(ATTACKER_ID))
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
        given(jwtTokenProvider.validateToken(ATTACKER_TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(ATTACKER_TOKEN)).willReturn(ATTACKER_ID);
        given(customUserDetailsService.loadUserById(ATTACKER_ID)).willReturn(attackerDetails);
    }

    @Test
    @DisplayName("다른 사용자의 신체 측정 기록 단건 조회 시 401 반환")
    void getMeasurement_whenNotOwner_returns401() throws Exception {
        given(bodyMeasurementService.getMeasurementById(ATTACKER_ID, MEASUREMENT_ID))
                .willThrow(new UnauthorizedException("다른 사용자의 신체 측정 기록에 접근할 수 없습니다."));

        mockMvc.perform(get("/api/v1/body-measurements/{id}", MEASUREMENT_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 신체 측정 기록 수정 시 401 반환")
    void updateMeasurement_whenNotOwner_returns401() throws Exception {
        given(bodyMeasurementService.updateMeasurement(eq(ATTACKER_ID), eq(MEASUREMENT_ID), any()))
                .willThrow(new UnauthorizedException("다른 사용자의 신체 측정 기록에 접근할 수 없습니다."));

        mockMvc.perform(patch("/api/v1/body-measurements/{id}", MEASUREMENT_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 70.0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 신체 측정 기록 삭제 시 401 반환")
    void deleteMeasurement_whenNotOwner_returns401() throws Exception {
        doThrow(new UnauthorizedException("다른 사용자의 신체 측정 기록에 접근할 수 없습니다."))
                .when(bodyMeasurementService).deleteMeasurement(ATTACKER_ID, MEASUREMENT_ID);

        mockMvc.perform(delete("/api/v1/body-measurements/{id}", MEASUREMENT_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증 토큰 없이 신체 측정 기록 조회 시 401 반환")
    void getMeasurement_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/body-measurements/{id}", MEASUREMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
