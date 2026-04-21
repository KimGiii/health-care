package com.healthcare.domain.bodymeasurement.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record MeasurementListResponse(
        List<MeasurementResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        boolean first,
        boolean last
) {
    public static MeasurementListResponse from(Page<MeasurementResponse> page) {
        return new MeasurementListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.isFirst(),
                page.isLast()
        );
    }
}
