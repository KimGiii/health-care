package com.healthcare.domain.diet.mealphoto.service;

import java.time.OffsetDateTime;

public interface MealPhotoStorageService {
    PresignedUpload generateUploadUrl(Long userId, String fileName, String contentType, long fileSizeBytes);
    String generateDownloadUrl(String storageKey);
    String loadAsDataUrl(String storageKey, String contentType);

    record PresignedUpload(String storageKey, String uploadUrl, OffsetDateTime expiresAt) {
    }
}
