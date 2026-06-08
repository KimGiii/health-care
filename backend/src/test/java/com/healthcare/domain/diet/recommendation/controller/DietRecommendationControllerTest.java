package com.healthcare.domain.diet.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse.NutrientSummary;
import com.healthcare.domain.diet.recommendation.usecase.DailyDietRecommendationUseCases;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietRecommendationController 단위 테스트")
class DietRecommendationControllerTest {

    @Mock private DailyDietRecommendationUseCases dailyDietRecommendationUseCases;

    @InjectMocks
    private DietRecommendationController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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
    @DisplayName("POST /api/v1/diet/recommendations/daily - 추천 성공 200 반환")
    void recommendDaily_success_returns200() throws Exception {
        DailyDietRecommendationRequest request = new DailyDietRecommendationRequest(
                LocalDate.now(),
                List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                false
        );
        DailyDietRecommendationResponse response = new DailyDietRecommendationResponse(
                LocalDate.now(),
                new NutritionTargets(2000, 150, 230, 67),
                List.of(),
                List.of(),
                new NutrientSummary(1800.0, 130.0, 210.0, 60.0),
                false,
                "이 추천은 참고용입니다."
        );
        given(dailyDietRecommendationUseCases.recommend(eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/diet/recommendations/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.calorieTarget").value(2000))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    @DisplayName("POST /api/v1/diet/recommendations/daily - 프로필 부족 시 422 반환")
    void recommendDaily_incompleteProfile_returns422() throws Exception {
        DailyDietRecommendationRequest request = new DailyDietRecommendationRequest(
                LocalDate.now(), List.of(MealType.LUNCH), false);
        given(dailyDietRecommendationUseCases.recommend(eq(USER_ID), any()))
                .willThrow(new BusinessRuleViolationException("프로필 정보가 부족합니다."));

        mockMvc.perform(post("/api/v1/diet/recommendations/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/v1/diet/recommendations/daily - 빈 mealTypes는 400 반환")
    void recommendDaily_emptyMealTypes_returns400() throws Exception {
        String body = """
                {"date":"%s","mealTypes":[],"strictAllergyMode":false}
                """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/v1/diet/recommendations/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
