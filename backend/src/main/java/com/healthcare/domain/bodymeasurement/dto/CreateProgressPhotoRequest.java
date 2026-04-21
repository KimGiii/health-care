package com.healthcare.domain.bodymeasurement.dto;

import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto.PhotoType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class CreateProgressPhotoRequest {

    @NotBlank(message = "storageKey는 필수입니다.")
    private String storageKey;

    @NotBlank(message = "contentType은 필수입니다.")
    private String contentType;

    @NotNull(message = "capturedAt은 필수입니다.")
    private OffsetDateTime capturedAt;

    @NotNull(message = "photoType은 필수입니다.")
    private PhotoType photoType;

    @DecimalMin(value = "20.0", message = "체중은 20kg 이상이어야 합니다.")
    @DecimalMax(value = "500.0", message = "체중은 500kg 이하여야 합니다.")
    private Double bodyWeightKg;

    @DecimalMin(value = "1.0", message = "체지방률은 1 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "체지방률은 100 이하여야 합니다.")
    private Double bodyFatPct;

    @DecimalMin(value = "1.0", message = "허리둘레는 1cm 이상이어야 합니다.")
    @DecimalMax(value = "500.0", message = "허리둘레는 500cm 이하여야 합니다.")
    private Double waistCm;

    private String notes;
    private Boolean isBaseline;
    private Long fileSizeBytes;
}
