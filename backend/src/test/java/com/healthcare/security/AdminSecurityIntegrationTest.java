package com.healthcare.security;

import com.healthcare.common.config.SecurityConfig;
import com.healthcare.common.filter.RateLimitingFilter;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.FoodCatalogAdminOperations;
import com.healthcare.domain.diet.controller.FoodCatalogAdminController;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FoodCatalogAdminController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        RateLimitingFilter.class
})
@DisplayName("Admin 보안 통합 테스트")
class AdminSecurityIntegrationTest {

    private static final String ADMIN_TOKEN = "admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoodCatalogAdminOperations adminOperations;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("관리자 카탈로그 import는 사용자 JWT 없이 operation token만으로 통과한다")
    void adminCatalogImport_withOperationTokenWithoutJwt_returns200() throws Exception {
        given(adminOperations.importProcessedFoods(eq(ADMIN_TOKEN), eq(10), eq(1)))
                .willReturn(new FoodCatalogBatchImportSummary(
                        FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                        1,
                        1,
                        1,
                        3,
                        0,
                        0,
                        false
                ));

        mockMvc.perform(post("/api/v1/admin/diet/catalog/import/processed-foods")
                        .queryParam("pageSize", "10")
                        .queryParam("maxPages", "1")
                        .header(AdminOperationGuard.HEADER_NAME, ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("MFDS_STANDARD_PROCESSED"))
                .andExpect(jsonPath("$.data.fetchedPageCount").value(1))
                .andExpect(jsonPath("$.data.attemptedCount").value(3))
                .andExpect(jsonPath("$.data.skippedRatio").value(0.0));
    }

    @Test
    @DisplayName("관리자 카탈로그 import는 operation token이 없으면 403을 반환한다")
    void adminCatalogImport_withoutOperationToken_returns403() throws Exception {
        given(adminOperations.importProcessedFoods(null, 10, 1))
                .willThrow(new AccessDeniedException("관리자 작업 권한이 없습니다."));

        mockMvc.perform(post("/api/v1/admin/diet/catalog/import/processed-foods")
                        .queryParam("pageSize", "10")
                        .queryParam("maxPages", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
