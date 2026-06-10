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

        RecordingFetcher fetcher = new RecordingFetcher(Map.of(
                2, new FoodCatalogImportPage<>(List.of(row("P002"), row("P003")), true),
                3, new FoodCatalogImportPage<>(List.of(row("P004")), false)
        ));
        CountingImporter importer = new CountingImporter();
        RecordingPageThrottle throttle = new RecordingPageThrottle();
        FoodCatalogImportBatchRunner runner = new FoodCatalogImportBatchRunner(checkpoints, throttle);

        FoodCatalogBatchImportSummary summary = runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                100,
                10,
                fetcher,
                importer
        );

        assertThat(fetcher.requestedPages()).containsExactly(2, 3);
        assertThat(importer.importedFoodCodes()).containsExactly("P002", "P003", "P004");
        assertThat(checkpoints.nextPage(FoodCatalogSource.MFDS_STANDARD_PROCESSED)).isEqualTo(4);
        assertThat(summary.source()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED);
        assertThat(summary.startPage()).isEqualTo(2);
        assertThat(summary.lastCompletedPage()).isEqualTo(3);
        assertThat(summary.fetchedPageCount()).isEqualTo(2);
        assertThat(summary.createdCount()).isEqualTo(3);
        assertThat(summary.updatedCount()).isZero();
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.exhausted()).isTrue();
        assertThat(throttle.completedPages()).containsExactly(2);
    }

    @Test
    @DisplayName("페이지 처리 중 실패하면 실패한 페이지를 완료 체크포인트로 저장하지 않는다")
    void importPages_doesNotAdvanceCheckpointWhenPageFails() {
        InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();
        checkpoints.markPageCompleted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, 1);
        List<Integer> requestedPages = new ArrayList<>();
        FoodCatalogImportPageFetcher<StandardFoodImportRow> fetcher = (pageNo, pageSize) -> {
            requestedPages.add(pageNo);
            if (pageNo == 3) {
                throw new IllegalStateException("식약처 API 응답 실패");
            }
            return new FoodCatalogImportPage<>(List.of(row("P002")), true);
        };
        CountingImporter importer = new CountingImporter();
        FoodCatalogImportBatchRunner runner = new FoodCatalogImportBatchRunner(checkpoints);

        assertThatThrownBy(() -> runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                100,
                10,
                fetcher,
                importer
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("식약처 API 응답 실패");

        assertThat(requestedPages).containsExactly(2, 3);
        assertThat(importer.importedFoodCodes()).containsExactly("P002");
        assertThat(checkpoints.nextPage(FoodCatalogSource.MFDS_STANDARD_PROCESSED)).isEqualTo(3);
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

    private static class RecordingFetcher implements FoodCatalogImportPageFetcher<StandardFoodImportRow> {
        private final Map<Integer, FoodCatalogImportPage<StandardFoodImportRow>> pages;
        private final List<Integer> requestedPages = new ArrayList<>();

        private RecordingFetcher(Map<Integer, FoodCatalogImportPage<StandardFoodImportRow>> pages) {
            this.pages = pages;
        }

        @Override
        public FoodCatalogImportPage<StandardFoodImportRow> fetch(int pageNo, int pageSize) {
            requestedPages.add(pageNo);
            return pages.getOrDefault(pageNo, new FoodCatalogImportPage<>(List.of(), false));
        }

        private List<Integer> requestedPages() {
            return requestedPages;
        }
    }

    private static class CountingImporter implements FoodCatalogPageImporter<StandardFoodImportRow> {
        private final List<String> importedFoodCodes = new ArrayList<>();

        @Override
        public FoodCatalogImportResult importRows(List<StandardFoodImportRow> rows) {
            rows.forEach(row -> importedFoodCodes.add(row.getFoodCode()));
            return new FoodCatalogImportResult(rows.size(), 0, 0);
        }

        private List<String> importedFoodCodes() {
            return importedFoodCodes;
        }
    }

    private static class RecordingPageThrottle implements FoodCatalogImportPageThrottle {
        private final List<Integer> completedPages = new ArrayList<>();

        @Override
        public void pauseAfterPage(FoodCatalogSource source, int completedPageNo) {
            completedPages.add(completedPageNo);
        }

        private List<Integer> completedPages() {
            return completedPages;
        }
    }
}
