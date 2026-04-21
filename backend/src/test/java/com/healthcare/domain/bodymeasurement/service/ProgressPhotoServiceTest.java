package com.healthcare.domain.bodymeasurement.service;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.bodymeasurement.dto.CreateProgressPhotoRequest;
import com.healthcare.domain.bodymeasurement.dto.InitiatePhotoUploadRequest;
import com.healthcare.domain.bodymeasurement.dto.InitiatePhotoUploadResponse;
import com.healthcare.domain.bodymeasurement.dto.ProgressPhotoListResponse;
import com.healthcare.domain.bodymeasurement.dto.ProgressPhotoResponse;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto.PhotoType;
import com.healthcare.domain.bodymeasurement.repository.ProgressPhotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressPhotoService 단위 테스트")
class ProgressPhotoServiceTest {

    @Mock private ProgressPhotoRepository progressPhotoRepository;
    @Mock private ProgressPhotoStorageService progressPhotoStorageService;

    @InjectMocks
    private ProgressPhotoService progressPhotoService;

    @Test
    @DisplayName("업로드 URL 발급 시 storageKey와 presigned URL을 반환한다")
    void initiateUpload_returnsPresignedUploadInfo() {
        InitiatePhotoUploadRequest request = new InitiatePhotoUploadRequest();
        setField(request, "fileName", "front.jpg");
        setField(request, "contentType", "image/jpeg");
        setField(request, "fileSizeBytes", 1_024L);

        given(progressPhotoStorageService.generateUploadUrl(1L, "front.jpg", "image/jpeg", 1_024L))
                .willReturn(new ProgressPhotoStorageService.PresignedUpload(
                        "progress-photos/1/test.jpg",
                        "https://upload.example.com/test",
                        OffsetDateTime.parse("2026-04-20T16:00:00+09:00")
                ));

        InitiatePhotoUploadResponse response = progressPhotoService.initiateUpload(1L, request);

        assertThat(response.getStorageKey()).isEqualTo("progress-photos/1/test.jpg");
        assertThat(response.getUploadUrl()).contains("upload.example.com");
    }

    @Test
    @DisplayName("사진 메타데이터 저장 시 baseline 사진은 기존 baseline을 해제한다")
    void registerPhoto_withBaseline_clearsPreviousBaseline() {
        CreateProgressPhotoRequest request = new CreateProgressPhotoRequest();
        setField(request, "storageKey", "progress-photos/1/new-photo.jpg");
        setField(request, "contentType", "image/jpeg");
        setField(request, "capturedAt", OffsetDateTime.parse("2026-04-20T09:00:00+09:00"));
        setField(request, "photoType", PhotoType.FRONT);
        setField(request, "isBaseline", true);
        setField(request, "fileSizeBytes", 2_048L);

        ProgressPhoto existingBaseline = ProgressPhoto.builder()
                .id(1L)
                .userId(1L)
                .photoType(PhotoType.FRONT)
                .capturedAt(OffsetDateTime.parse("2026-04-10T09:00:00+09:00"))
                .photoDate(LocalDate.of(2026, 4, 10))
                .storageKey("progress-photos/1/old-photo.jpg")
                .isBaseline(true)
                .build();

        given(progressPhotoRepository.findByStorageKeyAndUserId("progress-photos/1/new-photo.jpg", 1L))
                .willReturn(Optional.empty());
        given(progressPhotoRepository.findByUserIdAndPhotoTypeAndIsBaselineTrue(1L, PhotoType.FRONT))
                .willReturn(Optional.of(existingBaseline));
        given(progressPhotoRepository.save(any(ProgressPhoto.class))).willAnswer(invocation -> {
            ProgressPhoto photo = invocation.getArgument(0);
            if (photo.getId() == null) {
                setField(photo, "id", 2L);
            }
            return photo;
        });
        given(progressPhotoStorageService.generateDownloadUrl(anyString())).willReturn("https://download.example.com/photo");
        given(progressPhotoStorageService.generateDownloadUrl(isNull())).willReturn(null);

        ProgressPhotoResponse response = progressPhotoService.registerPhoto(1L, request);

        assertThat(response.getPhotoId()).isEqualTo(2L);
        assertThat(response.isBaseline()).isTrue();

        ArgumentCaptor<ProgressPhoto> photoCaptor = ArgumentCaptor.forClass(ProgressPhoto.class);
        verify(progressPhotoRepository, times(2)).save(photoCaptor.capture());
        assertThat(photoCaptor.getAllValues().get(0).isBaseline()).isFalse();
        assertThat(photoCaptor.getAllValues().get(1).isBaseline()).isTrue();
    }

    @Test
    @DisplayName("사진 목록 조회 시 signed URL이 포함된 페이지 결과를 반환한다")
    void listPhotos_returnsSignedUrlPage() {
        Pageable pageable = PageRequest.of(0, 20);
        ProgressPhoto photo = ProgressPhoto.builder()
                .id(3L)
                .userId(1L)
                .photoType(PhotoType.BACK)
                .capturedAt(OffsetDateTime.parse("2026-04-20T09:00:00+09:00"))
                .photoDate(LocalDate.of(2026, 4, 20))
                .storageKey("progress-photos/1/back-photo.jpg")
                .build();

        Page<ProgressPhoto> page = new PageImpl<>(List.of(photo), pageable, 1);
        given(progressPhotoRepository.findByUserIdAndCapturedAtRange(eq(1L), any(), any(), eq(pageable)))
                .willReturn(page);
        given(progressPhotoStorageService.generateDownloadUrl("progress-photos/1/back-photo.jpg"))
                .willReturn("https://download.example.com/back-photo");

        ProgressPhotoListResponse response = progressPhotoService.listPhotos(1L, null, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getSignedUrls().getOriginal()).contains("download.example.com");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("지원하지 않는 콘텐츠 타입이면 ValidationException이 발생한다")
    void initiateUpload_withInvalidContentType_throwsValidationException() {
        InitiatePhotoUploadRequest request = new InitiatePhotoUploadRequest();
        setField(request, "fileName", "front.gif");
        setField(request, "contentType", "image/gif");
        setField(request, "fileSizeBytes", 1_024L);

        assertThatThrownBy(() -> progressPhotoService.initiateUpload(1L, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("JPEG 또는 PNG");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
