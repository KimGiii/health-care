package com.healthcare.domain.diet.mealphoto.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.dto.CreateCustomFoodRequest;
import com.healthcare.domain.diet.dto.CreateDietLogRequest;
import com.healthcare.domain.diet.dto.CreateDietLogResponse;
import com.healthcare.domain.diet.dto.CreateFoodEntryRequest;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.mealphoto.dto.*;
import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysis;
import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysis.Status;
import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysisItem;
import com.healthcare.domain.diet.mealphoto.repository.MealPhotoAnalysisItemRepository;
import com.healthcare.domain.diet.mealphoto.repository.MealPhotoAnalysisRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.service.DietLogService;
import com.healthcare.domain.diet.service.FoodCatalogService;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealPhotoAnalysisService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final MealPhotoAnalysisRepository analysisRepository;
    private final MealPhotoAnalysisItemRepository itemRepository;
    private final MealPhotoStorageService mealPhotoStorageService;
    private final MealAnalysisProvider mealAnalysisProvider;
    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodCatalogService foodCatalogService;
    private final DietLogService dietLogService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public InitiateMealPhotoAnalysisResponse initiate(Long userId, InitiateMealPhotoAnalysisRequest request) {
        validateImageRequest(request.getContentType(), request.getFileSizeBytes());
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        MealPhotoStorageService.PresignedUpload upload = mealPhotoStorageService.generateUploadUrl(
                userId,
                request.getFileName(),
                request.getContentType(),
                request.getFileSizeBytes()
        );

        MealPhotoAnalysis analysis = analysisRepository.save(MealPhotoAnalysis.builder()
                .userId(userId)
                .storageKey(upload.storageKey())
                .contentType(request.getContentType())
                .fileSizeBytes(request.getFileSizeBytes())
                .capturedAt(request.getCapturedAt())
                .status(Status.INITIATED)
                .build());

        return InitiateMealPhotoAnalysisResponse.builder()
                .analysisId(analysis.getId())
                .storageKey(upload.storageKey())
                .uploadUrl(upload.uploadUrl())
                .previewUrl(mealPhotoStorageService.generateDownloadUrl(upload.storageKey()))
                .expiresAt(upload.expiresAt())
                .build();
    }

    @Transactional
    public MealPhotoAnalysisResponse analyze(Long userId, Long analysisId, AnalyzeMealPhotoRequest request) {
        MealPhotoAnalysis analysis = getOwnedAnalysis(userId, analysisId);
        if (analysis.getStatus() == Status.CONFIRMED) {
            throw new BusinessRuleViolationException("이미 확정된 식단 사진 분석입니다.");
        }

        String imageDataUrl = mealPhotoStorageService.loadAsDataUrl(analysis.getStorageKey(), analysis.getContentType());
        MealAnalysisProvider.AnalysisResult result = mealAnalysisProvider.analyze(imageDataUrl, analysis.getContentType());

        itemRepository.deleteByAnalysisId(analysis.getId());
        List<MealPhotoAnalysisItem> savedItems = new ArrayList<>();
        int order = 0;
        for (MealAnalysisProvider.DetectedItem detectedItem : result.items()) {
            FoodCatalog matched = matchFoodCatalog(userId, detectedItem.label());
            double servingG = positiveOrDefault(detectedItem.estimatedServingG(), 100.0);
            double calories = detectedItem.calories() != null ? detectedItem.calories() : 0.0;
            double proteinG = detectedItem.proteinG() != null ? detectedItem.proteinG() : 0.0;
            double carbsG = detectedItem.carbsG() != null ? detectedItem.carbsG() : 0.0;
            double fatG = detectedItem.fatG() != null ? detectedItem.fatG() : 0.0;

            if (matched != null) {
                double factor = servingG / 100.0;
                calories = round(matched.getCaloriesPer100g() * factor);
                proteinG = round(orZero(matched.getProteinPer100g()) * factor);
                carbsG = round(orZero(matched.getCarbsPer100g()) * factor);
                fatG = round(orZero(matched.getFatPer100g()) * factor);
            }

            MealPhotoAnalysisItem item = itemRepository.save(MealPhotoAnalysisItem.builder()
                    .analysisId(analysis.getId())
                    .itemOrder(order++)
                    .label(detectedItem.label())
                    .matchedFoodCatalogId(matched != null ? matched.getId() : null)
                    .estimatedServingG(round(servingG))
                    .calories(round(calories))
                    .proteinG(round(proteinG))
                    .carbsG(round(carbsG))
                    .fatG(round(fatG))
                    .confidence(detectedItem.confidence())
                    .needsReview(detectedItem.needsReview() || matched == null)
                    .unknownOrUncertain(detectedItem.unknownOrUncertain())
                    .build());
            savedItems.add(item);
        }

        try {
            analysis.markAnalyzed(
                    result.provider(),
                    result.analysisVersion(),
                    result.rawOutput(),
                    objectMapper.writeValueAsString(result.warnings())
            );
        } catch (Exception e) {
            analysis.markAnalyzed(result.provider(), result.analysisVersion(), result.rawOutput(), "[\"warning serialization failed\"]");
        }
        analysisRepository.save(analysis);
        return toResponse(analysis, savedItems);
    }

    @Transactional
    public ConfirmMealPhotoAnalysisResponse confirm(Long userId, Long analysisId, ConfirmMealPhotoAnalysisRequest request) {
        MealPhotoAnalysis analysis = getOwnedAnalysis(userId, analysisId);
        if (analysis.getStatus() == Status.CONFIRMED) {
            throw new BusinessRuleViolationException("이미 확정된 식단 사진 분석입니다.");
        }

        List<CreateFoodEntryRequest> entries = new ArrayList<>();
        for (ConfirmMealPhotoAnalysisRequest.ConfirmMealPhotoAnalysisItemRequest item : request.getItems()) {
            Long catalogId = item.getMatchedFoodCatalogId();
            if (catalogId == null) {
                CreateCustomFoodRequest customFoodRequest = CreateCustomFoodRequest.builder()
                        .name(item.getLabel())
                        .nameKo(item.getLabel())
                        .category(inferCategory(item.getLabel()))
                        .caloriesPer100g(toPer100g(item.getCalories(), item.getEstimatedServingG()))
                        .proteinPer100g(toPer100g(orZero(item.getProteinG()), item.getEstimatedServingG()))
                        .carbsPer100g(toPer100g(orZero(item.getCarbsG()), item.getEstimatedServingG()))
                        .fatPer100g(toPer100g(orZero(item.getFatG()), item.getEstimatedServingG()))
                        .build();
                catalogId = foodCatalogService.createCustomFood(userId, customFoodRequest).getId();
            }

            entries.add(CreateFoodEntryRequest.builder()
                    .foodCatalogId(catalogId)
                    .servingG(item.getEstimatedServingG())
                    .notes(item.getNotes())
                    .build());
        }

        CreateDietLogRequest createRequest = CreateDietLogRequest.builder()
                .logDate(request.getLogDate())
                .mealType(request.getMealType())
                .entries(entries)
                .notes(request.getNotes())
                .build();

        CreateDietLogResponse dietLog = dietLogService.createDietLog(userId, createRequest);
        analysis.markConfirmed();
        analysisRepository.save(analysis);

        return ConfirmMealPhotoAnalysisResponse.builder()
                .analysisId(analysis.getId())
                .status(analysis.getStatus().name())
                .dietLog(dietLog)
                .build();
    }

    public MealPhotoAnalysisResponse get(Long userId, Long analysisId) {
        MealPhotoAnalysis analysis = getOwnedAnalysis(userId, analysisId);
        List<MealPhotoAnalysisItem> items = itemRepository.findByAnalysisIdOrderByItemOrderAsc(analysisId);
        return toResponse(analysis, items);
    }

    private MealPhotoAnalysis getOwnedAnalysis(Long userId, Long analysisId) {
        return analysisRepository.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("MealPhotoAnalysis", analysisId));
    }

    private MealPhotoAnalysisResponse toResponse(MealPhotoAnalysis analysis, List<MealPhotoAnalysisItem> items) {
        List<String> warnings = parseWarnings(analysis.getAnalysisWarnings());
        List<MealPhotoAnalysisItemResponse> mappedItems = items.stream()
                .map(MealPhotoAnalysisItemResponse::from)
                .toList();

        return MealPhotoAnalysisResponse.from(
                analysis,
                mealPhotoStorageService.generateDownloadUrl(analysis.getStorageKey()),
                warnings,
                mappedItems
        );
    }

    private List<String> parseWarnings(String analysisWarnings) {
        if (analysisWarnings == null || analysisWarnings.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(analysisWarnings, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of(analysisWarnings);
        }
    }

    private FoodCatalog matchFoodCatalog(Long userId, String label) {
        return foodCatalogRepository.findAccessibleToUser(userId, label, null, false)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private FoodCategory inferCategory(String label) {
        String normalized = label.toLowerCase();
        if (normalized.contains("밥") || normalized.contains("rice") || normalized.contains("bread")) {
            return FoodCategory.GRAIN;
        }
        if (normalized.contains("닭") || normalized.contains("고기") || normalized.contains("egg") || normalized.contains("chicken")) {
            return FoodCategory.PROTEIN_SOURCE;
        }
        if (normalized.contains("샐러드") || normalized.contains("야채") || normalized.contains("채소")) {
            return FoodCategory.VEGETABLE;
        }
        if (normalized.contains("커피") || normalized.contains("라떼") || normalized.contains("tea")) {
            return FoodCategory.BEVERAGE;
        }
        return FoodCategory.OTHER;
    }

    private double toPer100g(Double total, Double servingG) {
        double grams = positiveOrDefault(servingG, 100.0);
        return round((orZero(total) / grams) * 100.0);
    }

    private void validateImageRequest(String contentType, long fileSizeBytes) {
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/jpg")
                && !contentType.equalsIgnoreCase("image/webp"))) {
            throw new ValidationException("JPEG, PNG, WEBP 이미지만 업로드할 수 있습니다.");
        }
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("파일 크기는 20MB 이하여야 합니다.");
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private double positiveOrDefault(Double value, double defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }
}
