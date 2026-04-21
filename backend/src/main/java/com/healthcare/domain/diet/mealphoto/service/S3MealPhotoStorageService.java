package com.healthcare.domain.diet.mealphoto.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3MealPhotoStorageService implements MealPhotoStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.meal-upload-prefix:meal-photos}")
    private String uploadPrefix;

    @Value("${app.photo.signed-url-ttl-minutes:15}")
    private long signedUrlTtlMinutes;

    @Override
    public PresignedUpload generateUploadUrl(Long userId, String fileName, String contentType, long fileSizeBytes) {
        String extension = extractExtension(fileName);
        String storageKey = "%s/%d/%s%s".formatted(uploadPrefix, userId, UUID.randomUUID(), extension);

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
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(signedUrlTtlMinutes)
        );
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        if (storageKey == null) {
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

    @Override
    public String loadAsDataUrl(String storageKey, String contentType) {
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());

        String encoded = Base64.getEncoder().encodeToString(bytes.asByteArray());
        return "data:%s;base64,%s".formatted(contentType, encoded);
    }

    private String extractExtension(String fileName) {
        int index = fileName != null ? fileName.lastIndexOf('.') : -1;
        return index >= 0 ? fileName.substring(index) : ".jpg";
    }
}
