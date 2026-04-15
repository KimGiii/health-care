package com.healthcare.domain.goals.dto;

import com.healthcare.domain.goals.entity.GoalCheckpoint;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalCheckpointResponse {

    private LocalDate checkpointDate;
    private BigDecimal actualValue;
    private BigDecimal projectedValue;
    private Boolean isOnTrack;

    public static GoalCheckpointResponse from(GoalCheckpoint checkpoint) {
        return GoalCheckpointResponse.builder()
                .checkpointDate(checkpoint.getCheckpointDate())
                .actualValue(checkpoint.getActualValue())
                .projectedValue(checkpoint.getProjectedValue())
                .isOnTrack(checkpoint.getIsOnTrack())
                .build();
    }
}
