package com.healthcare.domain.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "운동 날짜는 필수입니다.")
    private LocalDate sessionDate;

    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private String notes;

    @NotEmpty(message = "세트 정보는 최소 1개 이상 필요합니다.")
    @Valid
    private List<CreateSetRequest> sets;
}
