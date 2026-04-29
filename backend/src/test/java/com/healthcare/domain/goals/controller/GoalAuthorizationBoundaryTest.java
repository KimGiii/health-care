package com.healthcare.domain.goals.controller;

import com.healthcare.common.config.SecurityConfig;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.goals.service.GoalService;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GoalController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@DisplayName("목표 도메인 권한 경계 테스트")
class GoalAuthorizationBoundaryTest {

    private static final long ATTACKER_ID = 2L;
    private static final long GOAL_ID = 100L;
    private static final String ATTACKER_TOKEN = "attacker.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoalService goalService;

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
    @DisplayName("다른 사용자의 목표 단건 조회 시 401 반환")
    void getGoal_whenNotOwner_returns401() throws Exception {
        given(goalService.getGoalById(ATTACKER_ID, GOAL_ID))
                .willThrow(new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다."));

        mockMvc.perform(get("/api/v1/goals/{id}", GOAL_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 목표 진행률 조회 시 401 반환")
    void getGoalProgress_whenNotOwner_returns401() throws Exception {
        given(goalService.getGoalProgress(ATTACKER_ID, GOAL_ID))
                .willThrow(new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다."));

        mockMvc.perform(get("/api/v1/goals/{id}/progress", GOAL_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 목표 포기 시 401 반환")
    void abandonGoal_whenNotOwner_returns401() throws Exception {
        doThrow(new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다."))
                .when(goalService).abandonGoal(ATTACKER_ID, GOAL_ID);

        mockMvc.perform(delete("/api/v1/goals/{id}", GOAL_ID)
                        .header("Authorization", "Bearer " + ATTACKER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 목표 조회 시 401 반환")
    void getGoal_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/goals/{id}", GOAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰으로 목표 조회 시 401 반환")
    void getGoal_withMalformedToken_returns401() throws Exception {
        given(jwtTokenProvider.validateToken("rawtoken")).willReturn(true);
        given(jwtTokenProvider.getUserId("rawtoken")).willReturn(ATTACKER_ID);

        mockMvc.perform(get("/api/v1/goals/{id}", GOAL_ID)
                        .header("Authorization", "rawtoken"))
                .andExpect(status().isUnauthorized());
    }
}
