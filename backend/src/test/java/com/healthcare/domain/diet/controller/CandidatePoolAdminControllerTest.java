package com.healthcare.domain.diet.controller;

import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.CandidatePoolSummaryService;
import com.healthcare.domain.diet.admin.RecommendationCurationQueueEntry;
import com.healthcare.domain.diet.admin.RecommendationCurationQueueService;
import com.healthcare.domain.diet.admin.RevalidationQueueEntry;
import com.healthcare.domain.diet.admin.RevalidationQueueService;
import com.healthcare.domain.diet.admin.RevalidationReason;
import com.healthcare.domain.diet.admin.VerificationPriorityService;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidatePoolAdminController 단위 테스트")
class CandidatePoolAdminControllerTest {

    @Mock private AdminOperationGuard adminOperationGuard;
    @Mock private CandidatePoolSummaryService summaryService;
    @Mock private VerificationPriorityService priorityService;
    @Mock private RevalidationQueueService revalidationQueueService;
    @Mock private RecommendationCurationQueueService curationQueueService;

    @InjectMocks
    private CandidatePoolAdminController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("재검증 큐를 조회하면 200과 큐 항목을 반환한다")
    void revalidationQueue_returnsEntries() throws Exception {
        given(revalidationQueueService.queue(50)).willReturn(List.of(
                new RevalidationQueueEntry(1L, "회수된 메뉴", FoodCategory.PROTEIN_SOURCE,
                        FoodCatalogSource.BRAND_OFFICIAL, RevalidationReason.REVOKED_STALE_FACTS,
                        OffsetDateTime.parse("2026-06-19T00:00:00Z"), 30L)
        ));

        mockMvc.perform(get("/api/v1/admin/diet/candidate-pool/revalidation-queue")
                        .header(AdminOperationGuard.HEADER_NAME, "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].foodCatalogId").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("REVOKED_STALE_FACTS"));
    }

    @Test
    @DisplayName("admin 토큰이 거부되면 재검증 큐 조회는 403을 반환한다")
    void revalidationQueue_deniedToken_returns403() throws Exception {
        willThrow(new AccessDeniedException("invalid admin token"))
                .given(adminOperationGuard).assertAllowed(anyString());

        mockMvc.perform(get("/api/v1/admin/diet/candidate-pool/revalidation-queue")
                        .header(AdminOperationGuard.HEADER_NAME, "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("추천 큐레이션 대기열을 조회하면 200과 큐 항목을 반환한다")
    void curationQueue_returnsEntries() throws Exception {
        given(curationQueueService.queue(50)).willReturn(List.of(
                new RecommendationCurationQueueEntry(
                        10L,
                        FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB,
                        "P001",
                        "큐레이션 후보",
                        FoodCategory.PROTEIN_SOURCE,
                        20L,
                        true,
                        true,
                        false,
                        2_020.0)
        ));

        mockMvc.perform(get("/api/v1/admin/diet/candidate-pool/curation-queue")
                        .header(AdminOperationGuard.HEADER_NAME, "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].foodCatalogId").value(10))
                .andExpect(jsonPath("$.data[0].macroComplete").value(true))
                .andExpect(jsonPath("$.data[0].hasVerifiedServingOption").value(true));
    }
}
