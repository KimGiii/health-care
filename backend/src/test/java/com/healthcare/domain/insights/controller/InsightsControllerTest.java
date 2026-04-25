package com.healthcare.domain.insights.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.domain.insights.dto.ChangeAnalysisResponse;
import com.healthcare.domain.insights.dto.WeeklySummaryResponse;
import com.healthcare.domain.insights.service.InsightsService;
import com.healthcare.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightsController 단위 테스트")
class InsightsControllerTest {

    @Mock private InsightsService insightsService;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private InsightsController insightsController;

    private MockMvc mockMvc;

    private static final Long   USER_ID = 1L;
    private static final String BEARER  = "Bearer valid.test.token";
    private static final String TOKEN   = "valid.test.token";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(insightsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        lenient().when(jwtTokenProvider.getUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ─────────────────────────── GET /weekly-summary ───────────────────────────

    @Test
    @DisplayName("주간 요약 조회 성공 — 200, ISO-8601 날짜 형식, 운동/식단/신체/목표 필드 포함")
    void getWeeklySummary_success_returns200WithAllFields() throws Exception {
        WeeklySummaryResponse response = WeeklySummaryResponse.builder()
                .weekStart(LocalDate.of(2026, 4, 20))
                .weekEnd(LocalDate.of(2026, 4, 26))
                .weekOffset(0)
                .exerciseSessionCount(3)
                .totalExerciseMinutes(150)
                .totalCaloriesBurned(600.0)
                .dietLogCount(7)
                .avgDailyCalories(2100.0)
                .avgDailyProteinG(120.0)
                .latestWeightKg(75.0)
                .latestBodyFatPct(18.5)
                .weightChangeKg(-0.5)
                .activeGoalPercentComplete(40.0)
                .activeGoalTrackingStatus("ON_TRACK")
                .build();

        given(insightsService.getWeeklySummary(USER_ID, 0)).willReturn(response);

        mockMvc.perform(get("/api/v1/insights/weekly-summary")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weekStart").value("2026-04-20"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-04-26"))
                .andExpect(jsonPath("$.data.weekOffset").value(0))
                .andExpect(jsonPath("$.data.exerciseSessionCount").value(3))
                .andExpect(jsonPath("$.data.totalExerciseMinutes").value(150))
                .andExpect(jsonPath("$.data.totalCaloriesBurned").value(600.0))
                .andExpect(jsonPath("$.data.dietLogCount").value(7))
                .andExpect(jsonPath("$.data.latestWeightKg").value(75.0))
                .andExpect(jsonPath("$.data.weightChangeKg").value(-0.5))
                .andExpect(jsonPath("$.data.activeGoalPercentComplete").value(40.0))
                .andExpect(jsonPath("$.data.activeGoalTrackingStatus").value("ON_TRACK"));

        verify(insightsService).getWeeklySummary(USER_ID, 0);
    }

    @Test
    @DisplayName("weekOffset 파라미터 전달 시 이전 주 데이터 반환")
    void getWeeklySummary_withWeekOffset_passesOffsetToService() throws Exception {
        WeeklySummaryResponse response = WeeklySummaryResponse.builder()
                .weekStart(LocalDate.of(2026, 4, 13))
                .weekEnd(LocalDate.of(2026, 4, 19))
                .weekOffset(1)
                .exerciseSessionCount(0)
                .totalExerciseMinutes(0)
                .dietLogCount(0)
                .build();

        given(insightsService.getWeeklySummary(USER_ID, 1)).willReturn(response);

        mockMvc.perform(get("/api/v1/insights/weekly-summary")
                        .header("Authorization", BEARER)
                        .param("weekOffset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekOffset").value(1))
                .andExpect(jsonPath("$.data.weekStart").value("2026-04-13"));

        verify(insightsService).getWeeklySummary(USER_ID, 1);
    }

    @Test
    @DisplayName("운동·식단 기록 없는 주 — null 필드는 JSON에 포함되지 않거나 null")
    void getWeeklySummary_noData_returnsZeroCountsAndNullOptionalFields() throws Exception {
        WeeklySummaryResponse response = WeeklySummaryResponse.builder()
                .weekStart(LocalDate.of(2026, 4, 20))
                .weekEnd(LocalDate.of(2026, 4, 26))
                .weekOffset(0)
                .exerciseSessionCount(0)
                .totalExerciseMinutes(0)
                .dietLogCount(0)
                .build();

        given(insightsService.getWeeklySummary(USER_ID, 0)).willReturn(response);

        mockMvc.perform(get("/api/v1/insights/weekly-summary")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exerciseSessionCount").value(0))
                .andExpect(jsonPath("$.data.dietLogCount").value(0));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 주간 요약 요청 — 401 반환")
    void getWeeklySummary_missingAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/insights/weekly-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰으로 주간 요약 요청 — 401 반환")
    void getWeeklySummary_invalidBearerFormat_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/insights/weekly-summary")
                        .header("Authorization", "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─────────────────────────── GET /change-analysis ───────────────────────────

    @Test
    @DisplayName("변화 분석 조회 성공 — 200, 델타 값과 스냅샷·운동 요약 포함")
    void getChangeAnalysis_success_returns200WithDeltas() throws Exception {
        ChangeAnalysisResponse.BodySnapshot fromSnap = ChangeAnalysisResponse.BodySnapshot.builder()
                .measuredAt(LocalDate.of(2026, 1, 1))
                .weightKg(80.0).bodyFatPct(22.0).muscleMassKg(58.0)
                .build();
        ChangeAnalysisResponse.BodySnapshot toSnap = ChangeAnalysisResponse.BodySnapshot.builder()
                .measuredAt(LocalDate.of(2026, 4, 1))
                .weightKg(75.0).bodyFatPct(18.5).muscleMassKg(60.5)
                .build();

        ChangeAnalysisResponse response = ChangeAnalysisResponse.builder()
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 4, 1))
                .weightChangeKg(-5.0)
                .bodyFatPctChange(-3.5)
                .muscleMassChangeKg(2.5)
                .bmiChange(-1.7)
                .exerciseSessionCount(36)
                .totalExerciseMinutes(1800)
                .fromSnapshot(fromSnap)
                .toSnapshot(toSnap)
                .build();

        given(insightsService.getChangeAnalysis(
                eq(USER_ID),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 4, 1))
        )).willReturn(response);

        mockMvc.perform(get("/api/v1/insights/change-analysis")
                        .header("Authorization", BEARER)
                        .param("from", "2026-01-01")
                        .param("to", "2026-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fromDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data.toDate").value("2026-04-01"))
                .andExpect(jsonPath("$.data.weightChangeKg").value(-5.0))
                .andExpect(jsonPath("$.data.bodyFatPctChange").value(-3.5))
                .andExpect(jsonPath("$.data.muscleMassChangeKg").value(2.5))
                .andExpect(jsonPath("$.data.bmiChange").value(-1.7))
                .andExpect(jsonPath("$.data.exerciseSessionCount").value(36))
                .andExpect(jsonPath("$.data.totalExerciseMinutes").value(1800))
                .andExpect(jsonPath("$.data.fromSnapshot.weightKg").value(80.0))
                .andExpect(jsonPath("$.data.toSnapshot.weightKg").value(75.0));

        verify(insightsService).getChangeAnalysis(
                eq(USER_ID),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 4, 1))
        );
    }

    @Test
    @DisplayName("잘못된 날짜 형식(from=bad-date) — 422 반환")
    void getChangeAnalysis_invalidDateFormat_returns422() throws Exception {
        mockMvc.perform(get("/api/v1/insights/change-analysis")
                        .header("Authorization", BEARER)
                        .param("from", "bad-date")
                        .param("to", "2026-04-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("from > to 날짜 범위 역전 — 422 반환")
    void getChangeAnalysis_fromAfterTo_returns422() throws Exception {
        mockMvc.perform(get("/api/v1/insights/change-analysis")
                        .header("Authorization", BEARER)
                        .param("from", "2026-04-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 변화 분석 요청 — 401 반환")
    void getChangeAnalysis_missingAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/insights/change-analysis")
                        .param("from", "2026-01-01")
                        .param("to", "2026-04-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("from == to 당일 분석 — 서비스 정상 호출")
    void getChangeAnalysis_sameDayRange_callsService() throws Exception {
        LocalDate date = LocalDate.of(2026, 4, 24);
        ChangeAnalysisResponse response = ChangeAnalysisResponse.builder()
                .fromDate(date).toDate(date)
                .exerciseSessionCount(1).totalExerciseMinutes(60)
                .build();

        given(insightsService.getChangeAnalysis(USER_ID, date, date)).willReturn(response);

        mockMvc.perform(get("/api/v1/insights/change-analysis")
                        .header("Authorization", BEARER)
                        .param("from", "2026-04-24")
                        .param("to", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exerciseSessionCount").value(1));
    }
}
