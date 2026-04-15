package com.healthcare.domain.diet.external.client;

import com.healthcare.domain.diet.external.dto.ExternalFoodResult;

import java.util.List;

public interface PublicFoodApiClient {

    /**
     * 공공데이터 포털 식품 영양 정보 검색
     * (가공식품 API + 음식 API 통합 검색)
     *
     * @param query 검색어 (식품명)
     * @param page  0-based 페이지
     * @param size  페이지당 결과 수
     * @return 정규화된 결과 목록 (실패 시 예외 throw)
     */
    List<ExternalFoodResult> search(String query, int page, int size);
}