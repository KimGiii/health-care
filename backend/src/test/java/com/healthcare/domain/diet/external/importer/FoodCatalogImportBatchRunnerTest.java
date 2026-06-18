package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FoodCatalogImportBatchRunner")
class FoodCatalogImportBatchRunnerTest {

    @Test
    @DisplayName("마지막 완료 페이지 다음부터 순회하고 각 페이지 적재 후 체크포인트를 저장한다")
    void importPages_resumesFromNextCheckpointAndMarksCompletedPages() {
        InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();
        checkpoints.markPageCompleted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, 1);

        Map<Integer, FoodCatalogImportPage<StandardFoodImportRow>> pages = Map.of(
                2, new FoodCatalogImportPage<>(List.of(row("P002"), row("P003")), true),
                3, new FoodCatalogImportPage<>(List.of(row("P004")), false)
        );
        List<Integer> requestedPages = new ArrayList<>();
        List<String> importedFoodCodes = new ArrayList<>();
        List<Integer> throttledPages = new ArrayList<>();

        FoodCatalogImportPageFetcher<StandardFoodImportRow> fetcher = (pageNo, pageSize) -> {
            requestedPages.add(pageNo);
            return pages.getOrDefault(pageNo, new FoodCatalogImportPage<>(List.of(), false));
        };
        FoodCatalogPageImporter<StandardFoodImportRow> importer = rows -> {
            rows.forEach(r -> importedFoodCodes.add(r.getFoodCode()));
            return new FoodCatalogImportResult(rows.size(), 0, 0);
        };
        FoodCatalogImportPageThrottle throttle = (source, completedPageNo) -> throttledPages.add(completedPageNo);
        FoodCatalogImportBatchRunner runner = new FoodCatalogImportBatchRunner(checkpoints, throttle);

        FoodCatalogBatchImportSummary summary = runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, 100, 10, fetcher, importer);

        assertThat(requestedPages).containsExactly(2, 3);
        assertThat(importedFoodCodes).containsExactly("P002", "P003", "P004");
        assertThat(checkpoints.nextPage(FoodCatalogSource.MFDS_STANDARD_PROCESSED)).isEqualTo(4);
        assertThat(summary.source()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED);
        assertThat(summary.startPage()).isEqualTo(2);
        assertThat(summary.lastCompletedPage()).isEqualTo(3);
        assertThat(summary.fetchedPageCount()).isEqualTo(2);
        assertThat(summary.createdCount()).isEqualTo(3);
        assertThat(summary.updatedCount()).isZero();
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.exhausted()).isTrue();
        assertThat(throttledPages).containsExactly(2);
    }

    @Test
    @DisplayName("페이지 처리 중 실패하면 실패한 페이지를 완료 체크포인트로 저장하지 않는다")
    void importPages_doesNotAdvanceCheckpointWhenPageFails() {
        InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();
        checkpoints.markPageCompleted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, 1);

        List<Integer> requestedPages = new ArrayList<>();
        List<String> importedFoodCodes = new ArrayList<>();

        FoodCatalogImportPageFetcher<StandardFoodImportRow> fetcher = (pageNo, pageSize) -> {
            requestedPages.add(pageNo);
            if (pageNo == 3) {
                throw new IllegalStateException("식약처 API 응답 실패");
            }
            return new FoodCatalogImportPage<>(List.of(row("P002")), true);
        };
        FoodCatalogPageImporter<StandardFoodImportRow> importer = rows -> {
            rows.forEach(r -> importedFoodCodes.add(r.getFoodCode()));
            return new FoodCatalogImportResult(rows.size(), 0, 0);
        };
        FoodCatalogImportBatchRunner runner = new FoodCatalogImportBatchRunner(checkpoints);

        assertThatThrownBy(() -> runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, 100, 10, fetcher, importer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("식약처 API 응답 실패");

        assertThat(requestedPages).containsExactly(2, 3);
        assertThat(importedFoodCodes).containsExactly("P002");
        assertThat(checkpoints.nextPage(FoodCatalogSource.MFDS_STANDARD_PROCESSED)).isEqualTo(3);
    }

    @Test
    @DisplayName("운영 검증용으로 시도 수와 skip 비율을 summary에 노출한다")
    void importPages_exposesAttemptedCountAndSkippedRatio() {
        InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();

        FoodCatalogImportPageFetcher<StandardFoodImportRow> fetcher = (pageNo, pageSize) ->
                new FoodCatalogImportPage<>(List.of(row("P001"), row("P002"), row("P003"), row("P004")), false);
        FoodCatalogPageImporter<StandardFoodImportRow> importer = rows -> new FoodCatalogImportResult(1, 2, 1);
        FoodCatalogImportBatchRunner runner = new FoodCatalogImportBatchRunner(checkpoints);

        FoodCatalogBatchImportSummary summary = runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, 100, 1, fetcher, importer);

        assertThat(summary.attemptedCount()).isEqualTo(4);
        assertThat(summary.skippedRatio()).isEqualTo(0.25);
    }

    private StandardFoodImportRow row(String foodCode) {
        return StandardFoodImportRow.builder()
                .foodCode(foodCode)
                .foodName("테스트 식품 " + foodCode)
                .calories("100")
                .build();
    }

    private static class InMemoryCheckpointStore implements FoodCatalogImportCheckpointStore {
        private final Map<FoodCatalogSource, Integer> lastCompletedPages = new HashMap<>();

        @Override
        public int nextPage(FoodCatalogSource source) {
            return lastCompletedPages.getOrDefault(source, 0) + 1;
        }

        @Override
        public void markPageCompleted(FoodCatalogSource source, int pageNo) {
            lastCompletedPages.put(source, pageNo);
        }
    }
}
