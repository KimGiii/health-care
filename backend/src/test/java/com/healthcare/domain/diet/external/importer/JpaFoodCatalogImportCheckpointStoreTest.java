package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaFoodCatalogImportCheckpointStore")
class JpaFoodCatalogImportCheckpointStoreTest {

    @Mock
    private FoodCatalogImportCheckpointRepository repository;

    @Test
    @DisplayName("체크포인트가 없으면 1페이지부터 시작하고 완료 페이지를 새로 저장한다")
    void startsAtFirstPageAndCreatesCheckpointWhenMissing() {
        when(repository.findById(FoodCatalogSource.MFDS_STANDARD_PROCESSED))
                .thenReturn(Optional.empty());
        JpaFoodCatalogImportCheckpointStore store = new JpaFoodCatalogImportCheckpointStore(repository);

        int nextPage = store.nextPage(FoodCatalogSource.MFDS_STANDARD_PROCESSED);
        store.markPageCompleted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, 2);

        ArgumentCaptor<FoodCatalogImportCheckpoint> checkpointCaptor =
                ArgumentCaptor.forClass(FoodCatalogImportCheckpoint.class);
        verify(repository).save(checkpointCaptor.capture());
        FoodCatalogImportCheckpoint checkpoint = checkpointCaptor.getValue();

        assertThat(nextPage).isEqualTo(1);
        assertThat(checkpoint.getSource()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED);
        assertThat(checkpoint.getLastCompletedPage()).isEqualTo(2);
        assertThat(checkpoint.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("기존 체크포인트가 있으면 마지막 완료 페이지 다음부터 시작하고 기존 값을 갱신한다")
    void resumesAfterExistingCheckpointAndUpdatesIt() {
        FoodCatalogImportCheckpoint checkpoint =
                new FoodCatalogImportCheckpoint(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
        checkpoint.markPageCompleted(4);
        when(repository.findById(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB))
                .thenReturn(Optional.of(checkpoint));
        JpaFoodCatalogImportCheckpointStore store = new JpaFoodCatalogImportCheckpointStore(repository);

        int nextPage = store.nextPage(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
        store.markPageCompleted(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, 5);

        verify(repository).save(checkpoint);
        assertThat(nextPage).isEqualTo(5);
        assertThat(checkpoint.getLastCompletedPage()).isEqualTo(5);
    }
}
