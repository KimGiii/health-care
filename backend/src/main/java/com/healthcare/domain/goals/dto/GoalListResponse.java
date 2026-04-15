package com.healthcare.domain.goals.dto;

import com.healthcare.domain.goals.entity.Goal;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalListResponse {

    private List<GoalSummary> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private boolean first;
    private boolean last;

    public static GoalListResponse from(Page<Goal> page) {
        return GoalListResponse.builder()
                .content(page.getContent().stream().map(GoalSummary::from).toList())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
