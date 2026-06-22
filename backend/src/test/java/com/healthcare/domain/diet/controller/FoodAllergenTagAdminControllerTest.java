package com.healthcare.domain.diet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.allergen.FoodAllergenTagAdminOperations;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.dto.AddAllergenTagRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAddAllergenTagsRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAllergenTagResult;
import com.healthcare.domain.diet.allergen.dto.FoodAllergenTagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodAllergenTagAdminController")
class FoodAllergenTagAdminControllerTest {

    @Mock private FoodAllergenTagAdminOperations operations;

    @InjectMocks
    private FoodAllergenTagAdminController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TOKEN = "test-admin-token";
    private static final Long FOOD_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("POST / — 알러젠 태그를 추가하면 200을 반환한다")
    void add_returnsCreatedTag() throws Exception {
        AddAllergenTagRequest request = new AddAllergenTagRequest(
                FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.LABEL_DERIVED,
                AllergenDataSource.MFDS_CLASS, null
        );
        FoodAllergenTagResponse response = new FoodAllergenTagResponse(
                1L, FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.LABEL_DERIVED,
                AllergenDataSource.MFDS_CLASS, null, OffsetDateTime.now()
        );
        given(operations.add(eq(TOKEN), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/admin/diet/allergen-tags")
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergenTag").value("EGG"))
                .andExpect(jsonPath("$.data.foodCatalogId").value(1));
    }

    @Test
    @DisplayName("POST /bulk — 벌크 추가 결과를 반환한다")
    void bulkAdd_returnsResult() throws Exception {
        BulkAddAllergenTagsRequest request = new BulkAddAllergenTagsRequest(List.of(
                new AddAllergenTagRequest(FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.LABEL_DERIVED, AllergenDataSource.MFDS_CLASS, null),
                new AddAllergenTagRequest(FOOD_ID, AllergenTag.MILK, AllergenConfidenceLevel.LABEL_DERIVED, AllergenDataSource.MFDS_CLASS, null)
        ));
        given(operations.bulkAdd(eq(TOKEN), any())).willReturn(new BulkAllergenTagResult(2, 0));

        mockMvc.perform(post("/api/v1/admin/diet/allergen-tags/bulk")
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.skipped").value(0));
    }

    @Test
    @DisplayName("GET /food/{foodCatalogId} — 식품별 태그 목록을 반환한다")
    void getByFoodId_returnsTags() throws Exception {
        FoodAllergenTagResponse tag = new FoodAllergenTagResponse(
                1L, FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.DIRECT_VERIFIED,
                AllergenDataSource.MFDS_CLASS, null, OffsetDateTime.now()
        );
        given(operations.getByFoodId(TOKEN, FOOD_ID)).willReturn(List.of(tag));

        mockMvc.perform(get("/api/v1/admin/diet/allergen-tags/food/" + FOOD_ID)
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].allergenTag").value("EGG"));
    }

    @Test
    @DisplayName("GET /food/{foodCatalogId} — 식품 없으면 404를 반환한다")
    void getByFoodId_foodNotFound_returns404() throws Exception {
        given(operations.getByFoodId(TOKEN, 999L))
                .willThrow(new ResourceNotFoundException("식품 카탈로그를 찾을 수 없습니다: id=999"));

        mockMvc.perform(get("/api/v1/admin/diet/allergen-tags/food/999")
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /{tagId} — 태그를 삭제하면 200을 반환한다")
    void delete_existingTag_returns200() throws Exception {
        willDoNothing().given(operations).delete(TOKEN, 1L);

        mockMvc.perform(delete("/api/v1/admin/diet/allergen-tags/1")
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알러젠 태그 삭제 완료"));
    }

    @Test
    @DisplayName("DELETE /{tagId} — 태그 없으면 404를 반환한다")
    void delete_tagNotFound_returns404() throws Exception {
        willThrow(new ResourceNotFoundException("알러젠 태그를 찾을 수 없습니다: id=99"))
                .given(operations).delete(TOKEN, 99L);

        mockMvc.perform(delete("/api/v1/admin/diet/allergen-tags/99")
                        .header(AdminOperationGuard.HEADER_NAME, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("admin token 없이 요청하면 403을 반환한다")
    void add_withoutAdminToken_returns403() throws Exception {
        AddAllergenTagRequest request = new AddAllergenTagRequest(
                FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.UNKNOWN,
                AllergenDataSource.USER_CUSTOM, null
        );
        willThrow(new AccessDeniedException("관리자 작업 권한이 없습니다."))
                .given(operations).add(isNull(), any());

        mockMvc.perform(post("/api/v1/admin/diet/allergen-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
