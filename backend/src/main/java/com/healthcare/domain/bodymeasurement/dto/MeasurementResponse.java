package com.healthcare.domain.bodymeasurement.dto;

import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class MeasurementResponse {

    private Long id;
    private LocalDate measuredAt;

    private Double weightKg;
    private Double bodyFatPct;
    private Double muscleMassKg;
    private Double bmi;

    private Double chestCm;
    private Double waistCm;
    private Double hipCm;
    private Double thighCm;
    private Double armCm;

    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static MeasurementResponse from(BodyMeasurement m) {
        return MeasurementResponse.builder()
                .id(m.getId())
                .measuredAt(m.getMeasuredAt())
                .weightKg(m.getWeightKg())
                .bodyFatPct(m.getBodyFatPct())
                .muscleMassKg(m.getMuscleMassKg())
                .bmi(m.getBmi())
                .chestCm(m.getChestCm())
                .waistCm(m.getWaistCm())
                .hipCm(m.getHipCm())
                .thighCm(m.getThighCm())
                .armCm(m.getArmCm())
                .notes(m.getNotes())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
