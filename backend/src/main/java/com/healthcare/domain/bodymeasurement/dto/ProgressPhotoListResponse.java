package com.healthcare.domain.bodymeasurement.dto;

import java.util.List;

public record ProgressPhotoListResponse(
        List<ProgressPhotoResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        boolean first,
        boolean last
) {
}
