package com.healthcare.domain.bodymeasurement.service;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto.PhotoType;
import com.healthcare.domain.bodymeasurement.repository.ProgressPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgressPhotoService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final ProgressPhotoRepository progressPhotoRepository;
    private final ProgressPhotoStorageService progressPhotoStorageService;

    public InitiatePhotoUploadResponse initiateUpload(Long userId, InitiatePhotoUploadRequest request) {
        validateImageRequest(request.getContentType(), request.getFileSizeBytes());

        ProgressPhotoStorageService.PresignedUpload upload = progressPhotoStorageService.generateUploadUrl(
                userId,
                request.getFileName(),
                request.getContentType(),
                request.getFileSizeBytes()
        );

        return InitiatePhotoUploadResponse.builder()
                .storageKey(upload.storageKey())
                .uploadUrl(upload.uploadUrl())
                .expiresAt(upload.expiresAt())
                .build();
    }

    @Transactional
    public ProgressPhotoResponse registerPhoto(Long userId, CreateProgressPhotoRequest request) {
        validateImageRequest(request.getContentType(), request.getFileSizeBytes());
        validateStorageKeyOwnership(userId, request.getStorageKey());

        progressPhotoRepository.findByStorageKeyAndUserId(request.getStorageKey(), userId)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("이미 등록된 사진입니다.");
                });

        if (Boolean.TRUE.equals(request.getIsBaseline())) {
            progressPhotoRepository.findByUserIdAndPhotoTypeAndIsBaselineTrue(userId, request.getPhotoType())
                    .ifPresent(existing -> {
                        existing.clearBaseline();
                        progressPhotoRepository.save(existing);
                    });
        }

        ProgressPhoto photo = ProgressPhoto.builder()
                .userId(userId)
                .photoType(request.getPhotoType())
                .capturedAt(request.getCapturedAt())
                .photoDate(request.getCapturedAt().toLocalDate())
                .storageKey(request.getStorageKey())
                .contentType(request.getContentType())
                .fileSizeBytes(request.getFileSizeBytes())
                .bodyWeightKg(request.getBodyWeightKg())
                .bodyFatPct(request.getBodyFatPct())
                .waistCm(request.getWaistCm())
                .notes(request.getNotes())
                .isBaseline(Boolean.TRUE.equals(request.getIsBaseline()))
                .build();

        ProgressPhoto saved = progressPhotoRepository.save(photo);
        return toResponse(saved);
    }

    public ProgressPhotoListResponse listPhotos(Long userId, PhotoType photoType, LocalDate from, LocalDate to,
                                                Pageable pageable) {
        OffsetDateTime effectiveFrom = from != null
                ? from.atStartOfDay().atOffset(OffsetDateTime.now().getOffset())
                : OffsetDateTime.parse("2000-01-01T00:00:00+09:00");
        OffsetDateTime effectiveTo = to != null
                ? to.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()).minusNanos(1)
                : OffsetDateTime.now().plusYears(1);

        Page<ProgressPhoto> page = photoType != null
                ? progressPhotoRepository.findByUserIdAndPhotoTypeAndCapturedAtRange(userId, photoType, effectiveFrom, effectiveTo, pageable)
                : progressPhotoRepository.findByUserIdAndCapturedAtRange(userId, effectiveFrom, effectiveTo, pageable);

        Page<ProgressPhotoResponse> mapped = page.map(this::toResponse);
        return new ProgressPhotoListResponse(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.isFirst(),
                mapped.isLast()
        );
    }

    private ProgressPhotoResponse toResponse(ProgressPhoto photo) {
        return ProgressPhotoResponse.from(photo, ProgressPhotoSignedUrls.builder()
                .original(progressPhotoStorageService.generateDownloadUrl(photo.getStorageKey()))
                .thumbnail150(progressPhotoStorageService.generateDownloadUrl(photo.getThumbnailKey150()))
                .thumbnail400(progressPhotoStorageService.generateDownloadUrl(photo.getThumbnailKey400()))
                .thumbnail800(progressPhotoStorageService.generateDownloadUrl(photo.getThumbnailKey800()))
                .build());
    }

    private void validateImageRequest(String contentType, Long fileSizeBytes) {
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/jpg"))) {
            throw new ValidationException("JPEG 또는 PNG 이미지만 업로드할 수 있습니다.");
        }
        if (fileSizeBytes != null && fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("파일 크기는 20MB 이하여야 합니다.");
        }
    }

    private void validateStorageKeyOwnership(Long userId, String storageKey) {
        String userPrefix = "progress-photos/" + userId + "/";
        if (storageKey == null || !storageKey.startsWith(userPrefix)) {
            throw new ValidationException("현재 사용자에게 허용되지 않은 storageKey입니다.");
        }
    }
}
