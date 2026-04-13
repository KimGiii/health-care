package com.healthcare.domain.diet.external.client;

import com.healthcare.domain.diet.external.dto.ExternalFoodResult;

import java.util.List;
import java.util.Optional;

public interface OpenFoodFactsClient {

    /**
     * Open Food Facts 식품 텍스트 검색
     *
     * @param query 검색어
     * @param page  0-based 페이지
     * @param size  페이지당 결과 수
     * @return 정규화된 결과 목록 (실패 시 예외 throw)
     */
    List<ExternalFoodResult> search(String query, int page, int size);

    /**
     * Open Food Facts 바코드로 단일 식품 조회
     *
     * @param barcode EAN/UPC 바코드
     * @return 결과 (미등록 또는 실패 시 Empty)
     */
    Optional<ExternalFoodResult> findByBarcode(String barcode);
}
