package com.healthcare.domain.bodymeasurement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ProgressPhotoStorageService implements ProgressPhotoStorageService {

    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.upload-prefix:progress-photos}")
    private String uploadPrefix;

    @Value("${app.photo.signed-url-ttl-minutes:15}")
    private long signedUrlTtlMinutes;

    @Override
    public PresignedUpload generateUploadUrl(Long userId, String fileName, String contentType, long fileSizeBytes) {
        String extension = extractExtension(fileName, contentType);
        String storageKey = uploadPrefix + "/" + userId + "/" + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(fileSizeBytes)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(signedUrlTtlMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(
                storageKey,
                presigned.url().toString(),
                OffsetDateTime.now().plusMinutes(signedUrlTtlMinutes)
        );
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(signedUrlTtlMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private String extractExtension(String fileName, String contentType) {
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
                return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
            }
        }

        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }
        return ".jpg";
    }
}
