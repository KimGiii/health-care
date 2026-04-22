package com.healthcare.domain.goals.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.goals.dto.*;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.entity.Goal.GoalType;
import com.healthcare.domain.goals.service.GoalService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GoalController 컨트롤러 단위 테스트.
 *
 * standaloneSetup 방식으로 Spring Security 필터 없이 컨트롤러 로직과
 * 예외 처리(GlobalExceptionHandler)에 집중한다.
 * Spring Security 수준의 인증/인가 동작은 별도 통합 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoalController 단위 테스트")
class GoalControllerTest {

    @Mock private GoalService goalService;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private GoalController goalController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;
    private static final String BEARER = "Bearer valid.test.token";
    private static final String TOKEN  = "valid.test.token";

    /** 고정 날짜 — 테스트 결정성 확보 */
    private static final LocalDate START_DATE  = LocalDate.of(2026, 4, 22);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        // ISO-8601 날짜 직렬화 검증을 위해 application.yml 설정과 동일하게 구성
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(goalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        // 일부 테스트(인증 실패 케이스)에서는 getUserId가 호출되지 않으므로 lenient 사용
        lenient().when(jwtTokenProvider.getUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ─────────────────────────── GET /{id}/progress ───────────────────────────

    @Test
    @DisplayName("진행률 조회 성공 — 200, ISO 날짜 형식(\"2026-10-22\"), percentComplete/trackingStatus 포함")
    void getGoalProgress_success_returns200WithIsoDate() throws Exception {
        Long goalId = 10L;
        given(goalService.getGoalProgress(USER_ID, goalId))
                .willReturn(buildProgressResponse(goalId));

        mockMvc.perform(get("/api/v1/goals/{id}/progress", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.goalType").value("WEIGHT_LOSS"))
                .andExpect(jsonPath("$.data.percentComplete").value(50.0))
                .andExpect(jsonPath("$.data.trackingStatus").value("ON_TRACK"))
                .andExpect(jsonPath("$.data.trackingColor").value("GREEN"))
                .andExpect(jsonPath("$.data.isOnTrack").value(true))
                // ISO-8601 형식 검증 — 배열 [2026,10,22] 가 아닌 "2026-10-22" 이어야 한다
                .andExpect(jsonPath("$.data.targetDate").value("2026-10-22"))
                .andExpect(jsonPath("$.data.startDate").value("2026-04-22"))
                .andExpect(jsonPath("$.data.checkpoints").isArray());

        verify(goalService).getGoalProgress(USER_ID, goalId);
    }

    @Test
    @DisplayName("체크포인트 날짜도 ISO-8601 형식으로 직렬화된다")
    void getGoalProgress_withCheckpoints_checkpointDatesAreIso() throws Exception {
        Long goalId = 10L;
        GoalCheckpointResponse checkpoint = GoalCheckpointResponse.builder()
                .checkpointDate(LocalDate.of(2026, 5, 1))
                .actualValue(new BigDecimal("78.5"))
                .projectedValue(new BigDecimal("79.2"))
                .isOnTrack(true)
                .build();

        GoalProgressResponse response = GoalProgressResponse.builder()
                .goalId(goalId).goalType(GoalType.WEIGHT_LOSS)
                .targetValue(new BigDecimal("70.0")).targetUnit("kg")
                .targetDate(TARGET_DATE).startDate(START_DATE)
                .startValue(new BigDecimal("80.0")).currentValue(new BigDecimal("75.0"))
                .percentComplete(50.0)
                .daysRemaining(ChronoUnit.DAYS.between(LocalDate.now(), TARGET_DATE))
                .isOnTrack(true).trackingStatus("ON_TRACK").trackingColor("GREEN")
                .checkpoints(List.of(checkpoint))
                .build();

        given(goalService.getGoalProgress(USER_ID, goalId)).willReturn(response);

        mockMvc.perform(get("/api/v1/goals/{id}/progress", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkpoints[0].checkpointDate").value("2026-05-01"))
                .andExpect(jsonPath("$.data.checkpoints[0].actualValue").value(78.5))
                .andExpect(jsonPath("$.data.checkpoints[0].isOnTrack").value(true));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 요청 시 401 반환")
    void getGoalProgress_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/goals/{id}/progress", 10L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰으로 요청 시 401 반환")
    void getGoalProgress_invalidBearerFormat_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/goals/{id}/progress", 10L)
                        .header("Authorization", "invalid-token-without-prefix"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 목표 진행률 조회 — 404 반환")
    void getGoalProgress_notFound_returns404() throws Exception {
        Long goalId = 9999L;
        given(goalService.getGoalProgress(USER_ID, goalId))
                .willThrow(new ResourceNotFoundException("Goal", goalId));

        mockMvc.perform(get("/api/v1/goals/{id}/progress", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("타 사용자의 목표 진행률 조회 — 401 반환")
    void getGoalProgress_otherUserGoal_returns401() throws Exception {
        Long goalId = 10L;
        given(goalService.getGoalProgress(USER_ID, goalId))
                .willThrow(new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다."));

        mockMvc.perform(get("/api/v1/goals/{id}/progress", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("측정 기록이 없어 진행률 계산 불가 시 422 반환")
    void getGoalProgress_noMeasurements_returns422() throws Exception {
        Long goalId = 10L;
        given(goalService.getGoalProgress(USER_ID, goalId))
                .willThrow(new BusinessRuleViolationException("신체 측정 기록이 없어 목표 진행률을 계산할 수 없습니다."));

        mockMvc.perform(get("/api/v1/goals/{id}/progress", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // ─────────────────────────── POST / ───────────────────────────

    @Test
    @DisplayName("목표 생성 성공 — 201 반환, goalId와 status 포함")
    void createGoal_success_returns201() throws Exception {
        CreateGoalRequest request = CreateGoalRequest.builder()
                .goalType(GoalType.WEIGHT_LOSS)
                .targetValue(new BigDecimal("70.0")).targetUnit("kg")
                .targetDate(TARGET_DATE)
                .startValue(new BigDecimal("80.0"))
                .weeklyRateTarget(new BigDecimal("-0.5"))
                .build();

        GoalResponse goalResponse = GoalResponse.builder()
                .goalId(10L).goalType(GoalType.WEIGHT_LOSS)
                .targetValue(new BigDecimal("70.0")).targetUnit("kg")
                .targetDate(TARGET_DATE).startDate(START_DATE)
                .startValue(new BigDecimal("80.0")).status(GoalStatus.ACTIVE)
                .targets(GoalResponse.MacroTargets.builder().build())
                .build();

        given(goalService.createGoal(eq(USER_ID), any(CreateGoalRequest.class)))
                .willReturn(goalResponse);

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.goalId").value(10L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-10-22"));

        verify(goalService).createGoal(eq(USER_ID), any(CreateGoalRequest.class));
    }

    @Test
    @DisplayName("목표 생성 시 과거 날짜 — 422 반환")
    void createGoal_pastTargetDate_returns422() throws Exception {
        CreateGoalRequest request = CreateGoalRequest.builder()
                .goalType(GoalType.WEIGHT_LOSS)
                .targetValue(new BigDecimal("70.0")).targetUnit("kg")
                .targetDate(LocalDate.of(2025, 1, 1))
                .startValue(new BigDecimal("80.0"))
                .weeklyRateTarget(new BigDecimal("-0.5"))
                .build();

        given(goalService.createGoal(eq(USER_ID), any(CreateGoalRequest.class)))
                .willThrow(new BusinessRuleViolationException("목표 날짜는 오늘 이후여야 합니다."));

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // ─────────────────────────── GET / ───────────────────────────

    @Test
    @DisplayName("목표 목록 조회 성공 — 200, content 배열 포함")
    void listGoals_success_returns200() throws Exception {
        GoalListResponse listResponse = GoalListResponse.builder()
                .content(List.of())
                .totalElements(0L)
                .pageNumber(0).pageSize(20)
                .first(true).last(true)
                .build();
        given(goalService.listGoals(eq(USER_ID), any(), any()))
                .willReturn(listResponse);

        mockMvc.perform(get("/api/v1/goals")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ─────────────────────────── DELETE /{id} ───────────────────────────

    @Test
    @DisplayName("목표 포기 성공 — 200 반환")
    void abandonGoal_success_returns200() throws Exception {
        Long goalId = 10L;

        mockMvc.perform(delete("/api/v1/goals/{id}", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(goalService).abandonGoal(USER_ID, goalId);
    }

    @Test
    @DisplayName("이미 완료된 목표 포기 시도 — 422 반환")
    void abandonGoal_alreadyCompleted_returns422() throws Exception {
        Long goalId = 10L;
        doThrow(new BusinessRuleViolationException("이미 완료되었거나 포기된 목표입니다."))
                .when(goalService).abandonGoal(USER_ID, goalId);

        mockMvc.perform(delete("/api/v1/goals/{id}", goalId)
                        .header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private GoalProgressResponse buildProgressResponse(Long goalId) {
        return GoalProgressResponse.builder()
                .goalId(goalId)
                .goalType(GoalType.WEIGHT_LOSS)
                .targetValue(new BigDecimal("70.0")).targetUnit("kg")
                .targetDate(TARGET_DATE)
                .startDate(START_DATE)
                .startValue(new BigDecimal("80.0"))
                .currentValue(new BigDecimal("75.0"))
                .percentComplete(50.0)
                .daysRemaining(ChronoUnit.DAYS.between(LocalDate.now(), TARGET_DATE))
                .projectedCompletionDate(LocalDate.of(2026, 9, 22))
                .isOnTrack(true)
                .trackingStatus("ON_TRACK")
                .trackingColor("GREEN")
                .checkpoints(List.of())
                .build();
    }
}
