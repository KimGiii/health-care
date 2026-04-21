package com.healthcare.domain.bodymeasurement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class InitiatePhotoUploadRequest {

    @NotBlank(message = "파일명은 필수입니다.")
    private String fileName;

    @NotBlank(message = "콘텐츠 타입은 필수입니다.")
    private String contentType;

    @Min(value = 1, message = "파일 크기는 1 byte 이상이어야 합니다.")
    @Max(value = 20 * 1024 * 1024, message = "파일 크기는 20MB 이하여야 합니다.")
    private long fileSizeBytes;
}
