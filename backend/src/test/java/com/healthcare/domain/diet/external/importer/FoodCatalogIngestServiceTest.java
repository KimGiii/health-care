package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodCatalogIngestService")
class FoodCatalogIngestServiceTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    private FoodCatalogIngestService ingestService;

    @BeforeEach
    void setUp() {
        ingestService = new FoodCatalogIngestService(foodCatalogRepository);
    }

    @Test
    @DisplayName("기존 항목 갱신 시 PRESERVE_EXISTING은 추천 큐레이션을 유지한다")
    void ingest_preserveExisting_keepsCuration() {
        FoodCatalog existing = food("이전 이름", RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "나트륨 주의");
        FoodCatalog imported = food("새 이름", RecommendationStatus.SEARCH_ONLY, null);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        assertThat(result.updatedCount()).isEqualTo(1);
        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("새 이름");
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION);
        assertThat(saved.getRecommendationReason()).isEqualTo("나트륨 주의");
    }

    @Test
    @DisplayName("기존 항목 갱신 시 REPLACE_FROM_IMPORT는 추천 큐레이션을 가져온 값으로 교체한다")
    void ingest_replaceFromImport_replacesCuration() {
        FoodCatalog existing = food("이전 이름", RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "나트륨 주의");
        FoodCatalog imported = food("새 이름", RecommendationStatus.RECOMMENDABLE, null);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT
        );

        assertThat(result.updatedCount()).isEqualTo(1);
        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("새 이름");
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE);
        assertThat(saved.getRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("거절 candidate는 저장하지 않고 rejectedRows에 담는다")
    void ingest_rejectedCandidate_returnsRejectedRows() {
        FoodCatalogImportResult result = ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.rejected(3, "calories", "칼로리는 필수입니다.")),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).containsExactly(
                new FoodCatalogImportRejectedRow(3, "calories", "칼로리는 필수입니다.")
        );
        verify(foodCatalogRepository, never()).save(any());
    }

    private FoodCatalog food(String name, RecommendationStatus status, String reason) {
        return FoodCatalog.builder()
                .foodCode("P001")
                .source(FoodCatalogSource.MFDS_STANDARD_PROCESSED)
                .sourceDetail("15100066")
                .name(name)
                .nameKo(name)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .isCustom(false)
                .build();
    }
}
