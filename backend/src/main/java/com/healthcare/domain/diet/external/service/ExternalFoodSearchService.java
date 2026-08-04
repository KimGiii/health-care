package com.healthcare.domain.diet.external.service;

import com.healthcare.domain.diet.external.client.PublicFoodApiClient;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalFoodSearchService {

    private final PublicFoodApiClient publicFoodApiClient;

    /**
     * 공공데이터 포털 식품 영양정보 API에서 식품을 검색한다.
     * 결과는 인프로세스 Caffeine 캐시에 보관된다 (TTL·크기 상한: application.yml의 app.cache).
     */
    @Cacheable(value = "external-food-search",
               key = "'v2:' + #query + ':' + #source + ':' + #page + ':' + #size",
               unless = "#result.isEmpty()")
    public List<ExternalFoodResult> search(String query, FoodDataSource source, int page, int size) {
        if (source == FoodDataSource.PUBLIC_FOOD_API || source == FoodDataSource.ALL) {
            return fetchSafely("Public Food API",
                    () -> publicFoodApiClient.search(query, page, size));
        }
        return List.of();
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
