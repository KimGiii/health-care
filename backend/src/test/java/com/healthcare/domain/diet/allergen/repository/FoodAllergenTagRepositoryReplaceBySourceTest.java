package com.healthcare.domain.diet.allergen.repository;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("FoodAllergenTagRepository.replaceBySource")
class FoodAllergenTagRepositoryReplaceBySourceTest {

    private FoodAllergenTagRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(FoodAllergenTagRepository.class);
        doCallRealMethod().when(repo).replaceBySource(anyLong(), any(), any());
        doNothing().when(repo).deleteByFoodCatalogIdAndSource(anyLong(), any());
        when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("지정 소스 태그 삭제 후 새 태그를 저장한다")
    void replaceBySource_deletesSourceTagsThenSavesNew() {
        List<FoodAllergenTag> tags = List.of(
                FoodAllergenTag.builder()
                        .foodCatalogId(42L)
                        .allergenTag(AllergenTag.MILK)
                        .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                        .source(AllergenDataSource.BRAND_OFFICIAL)
                        .build()
        );

        repo.replaceBySource(42L, AllergenDataSource.BRAND_OFFICIAL, tags);

        verify(repo).deleteByFoodCatalogIdAndSource(42L, AllergenDataSource.BRAND_OFFICIAL);
        verify(repo).saveAll(tags);
    }

    @Test
    @DisplayName("빈 태그 목록이면 saveAll을 호출하지 않는다")
    void replaceBySource_emptyTags_onlyDeletes() {
        repo.replaceBySource(42L, AllergenDataSource.BRAND_OFFICIAL, List.of());

        verify(repo).deleteByFoodCatalogIdAndSource(42L, AllergenDataSource.BRAND_OFFICIAL);
        verify(repo, never()).saveAll(any());
    }

    @Test
    @DisplayName("삭제는 전달받은 소스 기준으로만 수행된다")
    void replaceBySource_usesExactSourceForDeletion() {
        repo.replaceBySource(10L, AllergenDataSource.MFDS_CLASS, List.of());

        verify(repo).deleteByFoodCatalogIdAndSource(10L, AllergenDataSource.MFDS_CLASS);
        verify(repo, never()).deleteByFoodCatalogIdAndSource(anyLong(), eq(AllergenDataSource.BRAND_OFFICIAL));
    }
}
