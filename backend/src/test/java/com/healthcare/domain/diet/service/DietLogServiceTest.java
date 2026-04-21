package com.healthcare.domain.diet.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodEntry;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodEntryRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * RED: DietLogService, 관련 Repository, DTO 클래스가 없으므로 컴파일 실패 상태.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DietLogService 단위 테스트")
class DietLogServiceTest {

    @Mock private DietLogRepository dietLogRepository;
    @Mock private FoodEntryRepository foodEntryRepository;
    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private DietLogService dietLogService;

    // ─────────────────────────── 식사 기록 생성 ───────────────────────────

    @Test
    @DisplayName("식품 항목 2개로 식사 기록 생성 시 칼로리·영양소 합계가 올바르게 계산된다")
    void createDietLog_withFoodEntries_calculatesMacros() {
        // given
        Long userId = 1L;
        // 닭가슴살 100g per 100g: 165kcal, protein 31g, carbs 0g, fat 3.6g
        FoodCatalog chicken = buildGlobalFood(10L, "Chicken Breast", FoodCategory.PROTEIN_SOURCE,
                165.0, 31.0, 0.0, 3.6);
        // 흰쌀밥 200g per 100g: 130kcal, protein 2.4g, carbs 28.7g, fat 0.3g
        FoodCatalog rice = buildGlobalFood(20L, "White Rice", FoodCategory.GRAIN,
                130.0, 2.4, 28.7, 0.3);

        CreateDietLogRequest request = CreateDietLogRequest.builder()
                .logDate(LocalDate.of(2026, 4, 13))
                .mealType(MealType.LUNCH)
                .entries(List.of(
                        CreateFoodEntryRequest.builder().foodCatalogId(10L).servingG(100.0).build(),
                        CreateFoodEntryRequest.builder().foodCatalogId(20L).servingG(200.0).build()
                ))
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(foodCatalogRepository.findById(10L)).willReturn(Optional.of(chicken));
        given(foodCatalogRepository.findById(20L)).willReturn(Optional.of(rice));

        // chicken 100g: 165kcal, rice 200g: 260kcal → total: 425kcal
        // chicken protein: 31g, rice protein: 4.8g → total: 35.8g
        // chicken carbs: 0g, rice carbs: 57.4g → total: 57.4g
        // chicken fat: 3.6g, rice fat: 0.6g → total: 4.2g
        DietLog savedLog = buildSavedDietLog(100L, userId, LocalDate.of(2026, 4, 13),
                MealType.LUNCH, 425.0, 35.8, 57.4, 4.2);
        given(dietLogRepository.save(any(DietLog.class))).willReturn(savedLog);
        given(foodEntryRepository.saveAll(anyList())).willReturn(List.of());

        // when
        CreateDietLogResponse response = dietLogService.createDietLog(userId, request);

        // then
        assertThat(response.getDietLogId()).isEqualTo(100L);
        assertThat(response.getTotalCalories()).isEqualTo(425.0);
        assertThat(response.getTotalProteinG()).isEqualTo(35.8);
        assertThat(response.getTotalCarbsG()).isEqualTo(57.4);
        assertThat(response.getTotalFatG()).isEqualTo(4.2);

        // 저장 엔티티 검증
        ArgumentCaptor<DietLog> logCaptor = ArgumentCaptor.forClass(DietLog.class);
        verify(dietLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTotalCalories()).isEqualTo(425.0);
        assertThat(logCaptor.getValue().getMealType()).isEqualTo(MealType.LUNCH);
    }

    @Test
    @DisplayName("존재하지 않는 foodCatalogId 사용 시 ResourceNotFoundException 발생")
    void createDietLog_invalidFoodCatalogId_throwsResourceNotFoundException() {
        // given
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId))
                .willReturn(Optional.of(buildUser(userId)));
        given(foodCatalogRepository.findById(999L)).willReturn(Optional.empty());

        CreateDietLogRequest request = CreateDietLogRequest.builder()
                .logDate(LocalDate.of(2026, 4, 13))
                .mealType(MealType.BREAKFAST)
                .entries(List.of(
                        CreateFoodEntryRequest.builder().foodCatalogId(999L).servingG(100.0).build()
                ))
                .build();

        // when & then
        assertThatThrownBy(() -> dietLogService.createDietLog(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 커스텀 식품 사용 시 ResourceNotFoundException 발생")
    void createDietLog_otherUserCustomFood_throwsResourceNotFoundException() {
        // given
        Long userId = 1L;
        Long anotherUserId = 99L;
        FoodCatalog otherUserFood = FoodCatalog.builder()
                .id(50L).name("Secret Food").category(FoodCategory.OTHER)
                .caloriesPer100g(100.0).proteinPer100g(5.0).carbsPer100g(10.0).fatPer100g(3.0)
                .isCustom(true).createdByUserId(anotherUserId)
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(userId))
                .willReturn(Optional.of(buildUser(userId)));
        given(foodCatalogRepository.findById(50L)).willReturn(Optional.of(otherUserFood));

        CreateDietLogRequest request = CreateDietLogRequest.builder()
                .logDate(LocalDate.of(2026, 4, 13))
                .mealType(MealType.DINNER)
                .entries(List.of(
                        CreateFoodEntryRequest.builder().foodCatalogId(50L).servingG(150.0).build()
                ))
                .build();

        // when & then
        assertThatThrownBy(() -> dietLogService.createDietLog(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 식사 기록 단건 조회 ───────────────────────────

    @Test
    @DisplayName("본인 식사 기록 조회 성공")
    void getDietLogById_success_returnsDietLogDetail() {
        // given
        Long userId = 1L;
        Long logId = 100L;
        DietLog dietLog = buildSavedDietLog(logId, userId, LocalDate.of(2026, 4, 13),
                MealType.LUNCH, 425.0, 35.8, 57.4, 4.2);

        given(dietLogRepository.findById(logId)).willReturn(Optional.of(dietLog));
        given(foodEntryRepository.findByDietLogIdOrderById(logId)).willReturn(List.of());

        // when
        DietLogDetailResponse response = dietLogService.getDietLogById(userId, logId);

        // then
        assertThat(response.getDietLogId()).isEqualTo(logId);
        assertThat(response.getMealType()).isEqualTo(MealType.LUNCH);
        assertThat(response.getTotalCalories()).isEqualTo(425.0);
    }

    @Test
    @DisplayName("다른 사용자의 식사 기록 조회 시 UnauthorizedException 발생")
    void getDietLogById_otherUserLog_throwsUnauthorizedException() {
        // given
        Long currentUserId = 1L;
        Long anotherUserId = 99L;
        Long logId = 100L;
        DietLog dietLog = buildSavedDietLog(logId, anotherUserId, LocalDate.of(2026, 4, 13),
                MealType.LUNCH, 425.0, 35.8, 57.4, 4.2);

        given(dietLogRepository.findById(logId)).willReturn(Optional.of(dietLog));

        // when & then
        assertThatThrownBy(() -> dietLogService.getDietLogById(currentUserId, logId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 식사 기록 ID 조회 시 ResourceNotFoundException 발생")
    void getDietLogById_notFound_throwsResourceNotFoundException() {
        // given
        given(dietLogRepository.findById(9999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dietLogService.getDietLogById(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 식사 기록 목록 조회 ───────────────────────────

    @Test
    @DisplayName("식사 기록 목록 조회 시 null 기간은 기본 날짜 범위로 치환되어 페이지네이션 결과를 반환한다")
    void listDietLogs_withNullDateRange_usesDefaultDatesAndReturnsPaginatedResults() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        DietLog log = buildSavedDietLog(1L, userId, LocalDate.of(2026, 4, 13),
                MealType.BREAKFAST, 300.0, 15.0, 40.0, 8.0);

        Page<DietLog> page = new PageImpl<>(List.of(log), pageable, 1);
        given(dietLogRepository.findByUserIdAndDateRange(eq(userId), any(LocalDate.class), any(LocalDate.class), eq(pageable)))
                .willReturn(page);

        // when
        DietLogListResponse response = dietLogService.listDietLogs(userId, null, null, pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(dietLogRepository).findByUserIdAndDateRange(eq(userId), fromCaptor.capture(), toCaptor.capture(), eq(pageable));
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now().plusYears(1));
    }

    // ─────────────────────────── 식사 기록 삭제 ───────────────────────────

    @Test
    @DisplayName("본인 식사 기록 소프트 삭제 성공")
    void deleteDietLog_success_softDeletesLog() {
        // given
        Long userId = 1L;
        Long logId = 100L;
        DietLog dietLog = buildSavedDietLog(logId, userId, LocalDate.of(2026, 4, 13),
                MealType.LUNCH, 425.0, 35.8, 57.4, 4.2);

        given(dietLogRepository.findById(logId)).willReturn(Optional.of(dietLog));

        // when
        dietLogService.deleteDietLog(userId, logId);

        // then — softDelete() 호출 후 deletedAt 이 설정되어야 함
        verify(dietLogRepository).findById(logId);
        ArgumentCaptor<DietLog> captor = ArgumentCaptor.forClass(DietLog.class);
        verify(dietLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 식사 기록 삭제 시 UnauthorizedException 발생")
    void deleteDietLog_otherUserLog_throwsUnauthorizedException() {
        // given
        Long logId = 100L;
        DietLog dietLog = buildSavedDietLog(logId, 99L, LocalDate.of(2026, 4, 13),
                MealType.DINNER, 500.0, 20.0, 60.0, 15.0);
        given(dietLogRepository.findById(logId)).willReturn(Optional.of(dietLog));

        // when & then
        assertThatThrownBy(() -> dietLogService.deleteDietLog(1L, logId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private FoodCatalog buildGlobalFood(Long id, String name, FoodCategory category,
            Double caloriesPer100g, Double proteinPer100g, Double carbsPer100g, Double fatPer100g) {
        return FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(caloriesPer100g).proteinPer100g(proteinPer100g)
                .carbsPer100g(carbsPer100g).fatPer100g(fatPer100g)
                .isCustom(false).createdByUserId(null)
                .build();
    }

    private User buildUser(Long id) {
        return User.builder()
                .id(id).email("test@example.com").passwordHash("hash")
                .displayName("Tester").build();
    }

    private DietLog buildSavedDietLog(Long id, Long userId, LocalDate logDate, MealType mealType,
            Double totalCalories, Double totalProteinG, Double totalCarbsG, Double totalFatG) {
        return DietLog.builder()
                .id(id).userId(userId).logDate(logDate).mealType(mealType)
                .totalCalories(totalCalories).totalProteinG(totalProteinG)
                .totalCarbsG(totalCarbsG).totalFatG(totalFatG)
                .build();
    }
}
