package com.healthcare.domain.diet.external.service;

import com.healthcare.domain.diet.external.client.OpenFoodFactsClient;
import com.healthcare.domain.diet.external.client.UsdaFoodDataClient;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalFoodSearchService {

    private final UsdaFoodDataClient usdaClient;
    private final OpenFoodFactsClient openFoodFactsClient;

    /**
     * 외부 API(USDA / Open Food Facts / 둘 다)에서 식품을 검색한다.
     * 결과는 Redis에 캐싱된다 (TTL: 30일, application.yml).
     */
    @Cacheable(value = "external-food-search",
               key = "#query + ':' + #source + ':' + #page + ':' + #size")
    public List<ExternalFoodResult> search(String query, FoodDataSource source, int page, int size) {
        List<ExternalFoodResult> results = new ArrayList<>();

        if (source == FoodDataSource.USDA || source == FoodDataSource.ALL) {
            results.addAll(fetchSafely("USDA", () -> usdaClient.search(query, page, size)));
        }
        if (source == FoodDataSource.OPEN_FOOD_FACTS || source == FoodDataSource.ALL) {
            results.addAll(fetchSafely("Open Food Facts",
                    () -> openFoodFactsClient.search(query, page, size)));
        }
        return results;
    }

    /**
     * 바코드로 Open Food Facts 식품을 조회한다.
     */
    @Cacheable(value = "external-food-barcode", key = "#barcode")
    public Optional<ExternalFoodResult> findByBarcode(String barcode) {
        try {
            return openFoodFactsClient.findByBarcode(barcode);
        } catch (Exception e) {
            log.warn("Open Food Facts barcode lookup failed for {}: {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    private List<ExternalFoodResult> fetchSafely(String sourceName,
            java.util.function.Supplier<List<ExternalFoodResult>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("{} 검색 실패: {}", sourceName, e.getMessage());
            return Collections.emptyList();
        }
    }
}
