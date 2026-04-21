package com.healthcare.domain.bodymeasurement.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class InitiatePhotoUploadResponse {
    private String storageKey;
    private String uploadUrl;
    private OffsetDateTime expiresAt;
}
