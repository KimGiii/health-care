package com.healthcare.domain.diet.mealphoto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.dto.CreateDietLogResponse;
import com.healthcare.domain.diet.entity.DietLog.MealType;
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
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MealPhotoAnalysisService 단위 테스트")
class MealPhotoAnalysisServiceTest {

    @Mock private MealPhotoAnalysisRepository analysisRepository;
    @Mock private MealPhotoAnalysisItemRepository itemRepository;
    @Mock private MealPhotoStorageService mealPhotoStorageService;
    @Mock private MealAnalysisProvider mealAnalysisProvider;
    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodCatalogService foodCatalogService;
    @Mock private DietLogService dietLogService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private MealPhotoAnalysisService mealPhotoAnalysisService;

    @BeforeEach
    void setUp() {
        mealPhotoAnalysisService = new MealPhotoAnalysisService(
                analysisRepository,
                itemRepository,
                mealPhotoStorageService,
                mealAnalysisProvider,
                foodCatalogRepository,
                foodCatalogService,
                dietLogService,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("업로드 초기화 시 분석 엔티티와 presigned URL을 반환한다")
    void initiate_createsAnalysisAndReturnsUploadInfo() {
        InitiateMealPhotoAnalysisRequest request = new InitiateMealPhotoAnalysisRequest();
        setField(request, "fileName", "meal.jpg");
        setField(request, "contentType", "image/jpeg");
        setField(request, "fileSizeBytes", 2048L);
        setField(request, "capturedAt", OffsetDateTime.parse("2026-04-21T12:00:00+09:00"));

        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(buildUser(1L)));
        given(mealPhotoStorageService.generateUploadUrl(1L, "meal.jpg", "image/jpeg", 2048L))
                .willReturn(new MealPhotoStorageService.PresignedUpload(
                        "meal-photos/1/test.jpg",
                        "https://upload.example.com/test.jpg",
                        OffsetDateTime.parse("2026-04-21T12:10:00+09:00")
                ));
        given(analysisRepository.save(any(MealPhotoAnalysis.class))).willAnswer(invocation -> {
            MealPhotoAnalysis analysis = invocation.getArgument(0);
            setField(analysis, "id", 10L);
            return analysis;
        });
        given(mealPhotoStorageService.generateDownloadUrl("meal-photos/1/test.jpg"))
                .willReturn("https://download.example.com/test.jpg");

        InitiateMealPhotoAnalysisResponse response = mealPhotoAnalysisService.initiate(1L, request);

        assertThat(response.getAnalysisId()).isEqualTo(10L);
        assertThat(response.getUploadUrl()).contains("upload.example.com");
        assertThat(response.getStorageKey()).isEqualTo("meal-photos/1/test.jpg");
    }

    @Test
    @DisplayName("분석 시 매칭된 카탈로그 기준으로 영양값을 보정한다")
    void analyze_matchesCatalogAndNormalizesNutrition() {
        MealPhotoAnalysis analysis = MealPhotoAnalysis.builder()
                .id(11L)
                .userId(1L)
                .storageKey("meal-photos/1/test.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(1024L)
                .capturedAt(OffsetDateTime.parse("2026-04-21T12:00:00+09:00"))
                .status(Status.INITIATED)
                .build();

        FoodCatalog rice = FoodCatalog.builder()
                .id(21L)
                .name("Rice")
                .nameKo("밥")
                .category(FoodCategory.GRAIN)
                .caloriesPer100g(130.0)
                .proteinPer100g(2.4)
                .carbsPer100g(28.7)
                .fatPer100g(0.3)
                .isCustom(false)
                .build();

        AnalyzeMealPhotoRequest request = new AnalyzeMealPhotoRequest();
        setField(request, "mealType", MealType.LUNCH);

        given(analysisRepository.findByIdAndUserId(11L, 1L)).willReturn(Optional.of(analysis));
        given(mealPhotoStorageService.loadAsDataUrl("meal-photos/1/test.jpg", "image/jpeg"))
                .willReturn("data:image/jpeg;base64,AAA");
        given(mealAnalysisProvider.analyze(anyString(), eq("image/jpeg")))
                .willReturn(new MealAnalysisProvider.AnalysisResult(
                        "fallback",
                        "heuristic-v1",
                        "{}",
                        List.of("warning"),
                        List.of(new MealAnalysisProvider.DetectedItem(
                                "밥", 150.0, 400.0, 10.0, 50.0, 3.0, 0.8, false, null
                        ))
                ));
        given(foodCatalogRepository.findAccessibleToUser(1L, "밥", null, false)).willReturn(List.of(rice));
        given(itemRepository.save(any(MealPhotoAnalysisItem.class))).willAnswer(invocation -> {
            MealPhotoAnalysisItem item = invocation.getArgument(0);
            setField(item, "id", 31L);
            return item;
        });
        given(mealPhotoStorageService.generateDownloadUrl("meal-photos/1/test.jpg"))
                .willReturn("https://download.example.com/test.jpg");

        MealPhotoAnalysisResponse response = mealPhotoAnalysisService.analyze(1L, 11L, request);

        assertThat(response.getDetectedItems()).hasSize(1);
        assertThat(response.getDetectedItems().get(0).getMatchedFoodCatalogId()).isEqualTo(21L);
        assertThat(response.getDetectedItems().get(0).getCalories()).isEqualTo(195.0);
        assertThat(response.getDetectedItems().get(0).isNeedsReview()).isFalse();
    }

    @Test
    @DisplayName("확정 시 매칭되지 않은 항목은 커스텀 식품으로 저장 후 DietLog 생성에 사용한다")
    void confirm_createsCustomFoodAndDietLog() {
        MealPhotoAnalysis analysis = MealPhotoAnalysis.builder()
                .id(15L)
                .userId(1L)
                .storageKey("meal-photos/1/test.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(1024L)
                .capturedAt(OffsetDateTime.parse("2026-04-21T12:00:00+09:00"))
                .status(Status.ANALYZED)
                .build();

        ConfirmMealPhotoAnalysisRequest request = new ConfirmMealPhotoAnalysisRequest();
        setField(request, "logDate", LocalDate.of(2026, 4, 21));
        setField(request, "mealType", MealType.DINNER);
        setField(request, "notes", "사진 기반 기록");

        ConfirmMealPhotoAnalysisRequest.ConfirmMealPhotoAnalysisItemRequest item =
                new ConfirmMealPhotoAnalysisRequest.ConfirmMealPhotoAnalysisItemRequest();
        setField(item, "analysisItemId", 1L);
        setField(item, "label", "김치볶음밥");
        setField(item, "estimatedServingG", 250.0);
        setField(item, "calories", 500.0);
        setField(item, "proteinG", 12.0);
        setField(item, "carbsG", 70.0);
        setField(item, "fatG", 18.0);
        setField(request, "items", List.of(item));

        given(analysisRepository.findByIdAndUserId(15L, 1L)).willReturn(Optional.of(analysis));
        given(foodCatalogService.createCustomFood(eq(1L), any())).willReturn(
                com.healthcare.domain.diet.dto.FoodCatalogResponse.builder()
                        .id(101L)
                        .name("김치볶음밥")
                        .nameKo("김치볶음밥")
                        .category(FoodCategory.OTHER)
                        .caloriesPer100g(200.0)
                        .custom(true)
                        .createdByUserId(1L)
                        .build()
        );
        given(dietLogService.createDietLog(eq(1L), any())).willReturn(CreateDietLogResponse.builder()
                .dietLogId(201L)
                .logDate(LocalDate.of(2026, 4, 21))
                .mealType(MealType.DINNER)
                .entryCount(1)
                .totalCalories(500.0)
                .totalProteinG(12.0)
                .totalCarbsG(70.0)
                .totalFatG(18.0)
                .build());

        ConfirmMealPhotoAnalysisResponse response = mealPhotoAnalysisService.confirm(1L, 15L, request);

        assertThat(response.getDietLog().getDietLogId()).isEqualTo(201L);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");

        ArgumentCaptor<com.healthcare.domain.diet.dto.CreateCustomFoodRequest> customCaptor =
                ArgumentCaptor.forClass(com.healthcare.domain.diet.dto.CreateCustomFoodRequest.class);
        verify(foodCatalogService).createCustomFood(eq(1L), customCaptor.capture());
        assertThat(customCaptor.getValue().getCaloriesPer100g()).isEqualTo(200.0);
    }

    private User buildUser(Long id) {
        User user = User.builder().email("user@test.com").passwordHash("hashed").build();
        setField(user, "id", id);
        return user;
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
