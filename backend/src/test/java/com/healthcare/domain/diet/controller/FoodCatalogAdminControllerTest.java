package com.healthcare.domain.diet.controller;

import com.healthcare.common.exception.GlobalExceptionHandler;
import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.FoodCatalogAdminOperations;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodCatalogAdminController 단위 테스트")
class FoodCatalogAdminControllerTest {

    @Mock private FoodCatalogAdminOperations adminOperations;

    @InjectMocks
    private FoodCatalogAdminController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효한 CSV 파일을 업로드하면 200과 import 결과를 반환한다")
    void importBrandMenuCsv_returnsSummary() throws Exception {
        String csvContent = """
                brand_name,menu_name,category,nutrition_basis,serving_size_g,calories,protein,carbs,fat,sodium,sugar,saturated_fat,source_url,last_verified_at,recommendation_status,recommendation_reason,allergen_tags,allergen_profile_verified
                서브웨이,로스트치킨 샌드위치,PROTEIN_SOURCE,PER_SERVING,232,320,24,42,5,720,6,1.5,https://subway.com,2026-01-01,RECOMMENDABLE,,밀,false
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "brand_menu.csv", "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        given(adminOperations.importBrandMenuCsv(any(), any())).willReturn(
                new FoodCatalogImportResult(1, 0, 0)
        );

        mockMvc.perform(multipart("/api/v1/admin/diet/catalog/import/brand-csv")
                        .file(file)
                        .header(AdminOperationGuard.HEADER_NAME, "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(0));
    }

    @Test
    @DisplayName("file 파라미터 없이 요청하면 성공 응답이 아니다")
    void importBrandMenuCsv_returnsErrorWhenFileMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/diet/catalog/import/brand-csv")
                        .header(AdminOperationGuard.HEADER_NAME, "test-admin-token"))
                .andDo(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("파일 없이도 200이 반환되었습니다");
                    }
                });
    }

    @Test
    @DisplayName("admin token 없이 브랜드 CSV import를 요청하면 403을 반환한다")
    void importBrandMenuCsv_withoutAdminToken_returnsForbidden() throws Exception {
        willThrow(new AccessDeniedException("관리자 작업 권한이 없습니다."))
                .given(adminOperations).importBrandMenuCsv(isNull(), any());
        MockMultipartFile file = new MockMultipartFile(
                "file", "brand_menu.csv", "text/csv", "header\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/diet/catalog/import/brand-csv").file(file))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
