package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FoodCatalogImportBatchRunner {

    private final FoodCatalogImportCheckpointStore checkpointStore;
    private final FoodCatalogImportPageThrottle pageThrottle;

    public FoodCatalogImportBatchRunner(FoodCatalogImportCheckpointStore checkpointStore) {
        this(checkpointStore, FoodCatalogImportPageThrottle.none());
    }

    @Autowired
    public FoodCatalogImportBatchRunner(
            FoodCatalogImportCheckpointStore checkpointStore,
            FoodCatalogImportPageThrottle pageThrottle) {
        this.checkpointStore = checkpointStore;
        this.pageThrottle = pageThrottle;
    }

    public <R> FoodCatalogBatchImportSummary importPages(
            FoodCatalogSource source,
            int pageSize,
            int maxPages,
            FoodCatalogImportPageFetcher<R> fetcher,
            FoodCatalogPageImporter<R> importer) {
        int startPage = checkpointStore.nextPage(source);
        int currentPage = startPage;
        int lastCompletedPage = startPage - 1;
        int fetchedPageCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        boolean exhausted = false;

        while (fetchedPageCount < maxPages) {
            FoodCatalogImportPage<R> page = fetcher.fetch(currentPage, pageSize);
            FoodCatalogImportResult result = importer.importRows(page.rows());

            createdCount += result.createdCount();
            updatedCount += result.updatedCount();
            skippedCount += result.skippedCount();
            checkpointStore.markPageCompleted(source, currentPage);

            lastCompletedPage = currentPage;
            fetchedPageCount++;

            if (!page.hasNext()) {
                exhausted = true;
                break;
            }
            if (fetchedPageCount < maxPages) {
                pageThrottle.pauseAfterPage(source, currentPage);
            }
            currentPage++;
        }

        return new FoodCatalogBatchImportSummary(
                source,
                startPage,
                lastCompletedPage,
                fetchedPageCount,
                createdCount,
                updatedCount,
                skippedCount,
                exhausted
        );
    }
}
