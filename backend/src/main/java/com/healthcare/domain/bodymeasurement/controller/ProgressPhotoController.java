package com.healthcare.domain.bodymeasurement.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto.PhotoType;
import com.healthcare.domain.bodymeasurement.service.ProgressPhotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/body-measurements/photos")
@RequiredArgsConstructor
public class ProgressPhotoController {

    private final ProgressPhotoService progressPhotoService;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<InitiatePhotoUploadResponse>> createUploadUrl(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody InitiatePhotoUploadRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        InitiatePhotoUploadResponse response = progressPhotoService.initiateUpload(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("업로드 URL이 생성되었습니다.", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProgressPhotoResponse>> registerPhoto(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateProgressPhotoRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ProgressPhotoResponse response = progressPhotoService.registerPhoto(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("진행 사진 메타데이터가 저장되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProgressPhotoListResponse>> listPhotos(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) PhotoType photoType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.parseLong(userDetails.getUsername());
        Pageable pageable = PageRequest.of(page, size, Sort.by("capturedAt").descending());
        ProgressPhotoListResponse response = progressPhotoService.listPhotos(userId, photoType, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
