package com.healthcare.domain.diet.external.client;

import com.healthcare.domain.diet.external.dto.ExternalFoodResult;

import java.util.List;

public interface UsdaFoodDataClient {

    /**
     * USDA FoodData Central 식품 검색
     *
     * @param query 검색어
     * @param page  0-based 페이지
     * @param size  페이지당 결과 수
     * @return 정규화된 결과 목록 (실패 시 예외 throw)
     */
    List<ExternalFoodResult> search(String query, int page, int size);
}
