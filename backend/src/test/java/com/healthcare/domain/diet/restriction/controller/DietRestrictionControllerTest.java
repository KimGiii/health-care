package com.healthcare.domain.diet.restriction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.restriction.dto.CreateDietRestrictionRequest;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.usecase.DietRestrictionUseCases;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietRestrictionController 단위 테스트")
class DietRestrictionControllerTest {

    @Mock private DietRestrictionUseCases dietRestrictionUseCases;

    @InjectMocks
    private DietRestrictionController controller;

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
    @DisplayName("GET /api/v1/diet/restrictions - 활성 제한 목록 반환")
    void listRestrictions_returnsActiveList() throws Exception {
        DietRestrictionResponse response = new DietRestrictionResponse(
                1L, RestrictionType.ALLERGY, TargetType.ALLERGEN_TAG,
                null, null, null, AllergenTag.MILK, OffsetDateTime.now());
        given(dietRestrictionUseCases.listRestrictions(USER_ID)).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/diet/restrictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].restrictionType").value("ALLERGY"))
                .andExpect(jsonPath("$.data[0].allergenTag").value("MILK"));
    }

    @Test
    @DisplayName("POST /api/v1/diet/restrictions - 알러젠 태그 제한 생성")
    void createRestriction_allergenTag_returns201() throws Exception {
        CreateDietRestrictionRequest request = CreateDietRestrictionRequest.builder()
                .restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG)
                .allergenTag(AllergenTag.MILK)
                .build();
        DietRestrictionResponse response = new DietRestrictionResponse(
                1L, RestrictionType.ALLERGY, TargetType.ALLERGEN_TAG,
                null, null, null, AllergenTag.MILK, OffsetDateTime.now());
        given(dietRestrictionUseCases.createRestriction(eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/diet/restrictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allergenTag").value("MILK"));
    }

    @Test
    @DisplayName("POST /api/v1/diet/restrictions - 중복 생성 시 422 반환")
    void createRestriction_duplicate_returns422() throws Exception {
        CreateDietRestrictionRequest request = CreateDietRestrictionRequest.builder()
                .restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG)
                .allergenTag(AllergenTag.MILK)
                .build();
        given(dietRestrictionUseCases.createRestriction(eq(USER_ID), any()))
                .willThrow(new BusinessRuleViolationException("이미 동일한 제한 조건이 존재합니다."));

        mockMvc.perform(post("/api/v1/diet/restrictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DELETE /api/v1/diet/restrictions/{id} - 본인 제한 삭제 성공 200")
    void deleteRestriction_ownRecord_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/diet/restrictions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/diet/restrictions/{id} - 타인 제한 삭제 시 401 반환")
    void deleteRestriction_otherUser_returns401() throws Exception {
        doThrow(new UnauthorizedException("본인의 제한 조건만 삭제할 수 있습니다."))
                .when(dietRestrictionUseCases).deleteRestriction(USER_ID, 99L);

        mockMvc.perform(delete("/api/v1/diet/restrictions/99"))
                .andExpect(status().isUnauthorized());
    }
}
