package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.DietLog;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class DietLogListResponse {

    private final List<DietLogSummary> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public static DietLogListResponse from(Page<DietLog> page) {
        return DietLogListResponse.builder()
                .content(page.getContent().stream().map(DietLogSummary::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
