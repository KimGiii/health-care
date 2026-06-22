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

    @Mock
    private com.healthcare.domain.diet.repository.FoodServingOptionRepository servingOptionRepository;

    @Mock
    private com.healthcare.domain.diet.external.dedup.CanonicalDedupResolver dedupResolver;

    private FoodCatalogIngestService ingestService;

    @BeforeEach
    void setUp() {
        ingestService = new FoodCatalogIngestService(
                foodCatalogRepository,
                servingOptionRepository,
                new ServingOptionDeriver(),
                dedupResolver);
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
    @DisplayName("PRESERVE_EXISTING에서 추천 후보의 핵심 영양값이 바뀌면 자격을 회수하고 재검증 사유를 남긴다")
    void ingest_preserveExisting_revokesWhenNutritionFactsChanged() {
        FoodCatalog existing = food("이름", RecommendationStatus.RECOMMENDABLE, null);
        FoodCatalog imported = foodWithCalories("이름", RecommendationStatus.SEARCH_ONLY, null, 250.0);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getCaloriesPer100g()).isEqualTo(250.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getRecommendationReason())
                .isEqualTo(FoodCatalog.STALE_FACTS_REVALIDATION_REASON);
    }

    @Test
    @DisplayName("PRESERVE_EXISTING에서 영양 사실이 같으면(이름만 변경) 추천 자격을 유지한다")
    void ingest_preserveExisting_keepsCandidateWhenFactsUnchanged() {
        FoodCatalog existing = food("이전 이름", RecommendationStatus.RECOMMENDABLE, null);
        FoodCatalog imported = food("새 이름", RecommendationStatus.SEARCH_ONLY, null);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("새 이름");
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE);
        assertThat(saved.getRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("PRESERVE_EXISTING에서 영양값 미세 변동(상대 5% 이하)은 추천 자격을 유지한다")
    void ingest_preserveExisting_keepsCandidateOnMinorFactChange() {
        FoodCatalog existing = foodWithCalories("이름", RecommendationStatus.RECOMMENDABLE, null, 165.0);
        FoodCatalog imported = foodWithCalories("이름", RecommendationStatus.SEARCH_ONLY, null, 169.0); // +2.4%
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getCaloriesPer100g()).isEqualTo(169.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE);
        assertThat(saved.getRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("PRESERVE_EXISTING에서 이미 추천 후보가 아닌 항목은 영양값이 바뀌어도 회수하지 않는다")
    void ingest_preserveExisting_nonCandidateUnaffectedByFactChange() {
        FoodCatalog existing = food("이름", RecommendationStatus.SEARCH_ONLY, null);
        FoodCatalog imported = foodWithCalories("이름", RecommendationStatus.SEARCH_ONLY, null, 250.0);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("REPLACE_FROM_IMPORT는 영양값이 바뀌어도 회수가 아니라 가져온 큐레이션으로 교체한다")
    void ingest_replaceFromImport_doesNotRevokeOnFactChange() {
        FoodCatalog existing = food("이름", RecommendationStatus.RECOMMENDABLE, null);
        FoodCatalog imported = foodWithCalories("이름", RecommendationStatus.RECOMMENDABLE, null, 250.0);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT
        );

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
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

    @Test
    @DisplayName("dedup 패자(superseded)로 강등된 신규 행은 제공량 옵션을 생성하지 않고 정리한다")
    void ingest_supersededRow_skipsServingOptionDerivation() {
        FoodCatalog imported = FoodCatalog.builder()
                .id(1L)
                .foodCode("D001")
                .source(FoodCatalogSource.MFDS_STANDARD_DISH)
                .name("김치찌개").nameKo("김치찌개")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(120.0)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .isCustom(false)
                .build();
        FoodCatalog canonical = FoodCatalog.builder().id(99L).name("김치찌개").nameKo("김치찌개")
                .category(FoodCategory.PROTEIN_SOURCE).caloriesPer100g(120.0)
                .source(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB).isCustom(false).build();
        given(foodCatalogRepository.findBySourceAndFoodCode(any(), any())).willReturn(Optional.empty());
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // resolver가 더 높은 우선순위 대표(영양DB) 존재로 이 행을 패자로 강등했다고 가정
        org.mockito.BDDMockito.willAnswer(invocation -> {
            ((FoodCatalog) invocation.getArgument(0)).supersedeBy(canonical);
            return null;
        }).given(dedupResolver).resolve(any(FoodCatalog.class));

        ingestService.ingest(
                List.of(FoodCatalogIngestCandidate.accepted(imported)),
                FoodCatalogIngestCurationMode.PRESERVE_EXISTING
        );

        // 패자는 옵션을 생성하지 않고 남은 옵션만 정리한다(옵션 행 폭증 방지)
        verify(servingOptionRepository).deleteByFoodCatalogId(1L);
        verify(servingOptionRepository, never()).saveAll(any());
    }

    private FoodCatalog food(String name, RecommendationStatus status, String reason) {
        return foodWithCalories(name, status, reason, 165.0);
    }

    private FoodCatalog foodWithCalories(
            String name, RecommendationStatus status, String reason, double calories) {
        return FoodCatalog.builder()
                .foodCode("P001")
                .source(FoodCatalogSource.MFDS_STANDARD_PROCESSED)
                .sourceDetail("15100066")
                .name(name)
                .nameKo(name)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(calories)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .isCustom(false)
                .build();
    }
}
