package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodCatalogDuplicateReportService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodCatalogDuplicateCandidateReporter reporter;

    @Transactional(readOnly = true)
    public FoodCatalogDuplicateReportResponse generateReport() {
        List<FoodCatalog> catalogs = foodCatalogRepository.findByIsCustomFalse();
        FoodCatalogDuplicateCandidateReport report = reporter.report(catalogs);
        return FoodCatalogDuplicateReportResponse.from(report);
    }
}
