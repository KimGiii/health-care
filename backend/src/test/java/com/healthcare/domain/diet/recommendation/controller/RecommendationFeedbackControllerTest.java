package com.healthcare.domain.diet.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationFeedbackReason;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationFeedbackService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationFeedbackController 단위 테스트")
class RecommendationFeedbackControllerTest {

    @Mock private RecommendationFeedbackService feedbackService;

    @InjectMocks
    private RecommendationFeedbackController feedbackController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(feedbackController)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        SecurityTestSupport.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clear();
    }

    @Test
    @DisplayName("유효한 snapshotId와 reason으로 피드백 요청 시 204를 반환한다")
    void postFeedback_validRequest_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/diet/recommendations/10/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_HUNGRY\"}"))
                .andExpect(status().isNoContent());

        verify(feedbackService).recordFeedback(eq(USER_ID), eq(10L),
                eq(RecommendationFeedbackReason.NOT_HUNGRY));
    }

    @Test
    @DisplayName("존재하지 않는 snapshotId면 404를 반환한다")
    void postFeedback_unknownSnapshot_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("RecommendationSnapshot", 999L))
                .when(feedbackService).recordFeedback(eq(USER_ID), eq(999L),
                        eq(RecommendationFeedbackReason.NO_REASON));

        mockMvc.perform(post("/api/v1/diet/recommendations/999/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NO_REASON\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reason 없이 요청 시 400을 반환한다")
    void postFeedback_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/diet/recommendations/10/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
