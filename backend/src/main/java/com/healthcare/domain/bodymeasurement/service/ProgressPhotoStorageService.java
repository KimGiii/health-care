package com.healthcare.domain.bodymeasurement.service;

import java.time.OffsetDateTime;

public interface ProgressPhotoStorageService {

    PresignedUpload generateUploadUrl(Long userId, String fileName, String contentType, long fileSizeBytes);

    String generateDownloadUrl(String storageKey);

    record PresignedUpload(String storageKey, String uploadUrl, OffsetDateTime expiresAt) {
    }
}
