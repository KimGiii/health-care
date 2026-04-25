package com.healthcare.domain.bodymeasurement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class ProgressPhotoResponse {
    private Long photoId;
    private OffsetDateTime capturedAt;
    private ProgressPhoto.PhotoType photoType;
    @JsonProperty("isBaseline")
    private boolean isBaseline;
    private boolean exifStripped;
    private boolean isPrivate;
    private String thumbnailStatus;
    private ProgressPhotoSignedUrls signedUrls;
    private Double bodyWeightKg;
    private Double bodyFatPct;
    private Double waistCm;
    private String notes;

    public static ProgressPhotoResponse from(ProgressPhoto photo, ProgressPhotoSignedUrls signedUrls) {
        return ProgressPhotoResponse.builder()
                .photoId(photo.getId())
                .capturedAt(photo.getCapturedAt())
                .photoType(photo.getPhotoType())
                .isBaseline(photo.isBaseline())
                .exifStripped(photo.isExifStripped())
                .isPrivate(photo.isPrivate())
                .thumbnailStatus(photo.getThumbnailKey400() != null ? "READY" : "PENDING")
                .signedUrls(signedUrls)
                .bodyWeightKg(photo.getBodyWeightKg())
                .bodyFatPct(photo.getBodyFatPct())
                .waistCm(photo.getWaistCm())
                .notes(photo.getNotes())
                .build();
    }
}
