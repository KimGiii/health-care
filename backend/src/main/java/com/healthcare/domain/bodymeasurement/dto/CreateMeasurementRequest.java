package com.healthcare.domain.bodymeasurement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class CreateMeasurementRequest {

    @NotNull(message = "측정 날짜는 필수입니다.")
    private LocalDate measuredAt;

    @Positive(message = "체중은 양수여야 합니다.")
    private Double weightKg;

    @Positive(message = "체지방률은 양수여야 합니다.")
    private Double bodyFatPct;

    @Positive(message = "근육량은 양수여야 합니다.")
    private Double muscleMassKg;

    @Positive(message = "BMI는 양수여야 합니다.")
    private Double bmi;

    @Positive(message = "가슴 둘레는 양수여야 합니다.")
    private Double chestCm;

    @Positive(message = "허리 둘레는 양수여야 합니다.")
    private Double waistCm;

    @Positive(message = "엉덩이 둘레는 양수여야 합니다.")
    private Double hipCm;

    @Positive(message = "허벅지 둘레는 양수여야 합니다.")
    private Double thighCm;

    @Positive(message = "팔 둘레는 양수여야 합니다.")
    private Double armCm;

    private String notes;
}
