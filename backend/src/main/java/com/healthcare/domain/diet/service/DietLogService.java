package com.healthcare.domain.diet.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLogNutritionTotals;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodEntry;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodEntryRepository;
import com.healthcare.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DietLogService {

    private final DietLogRepository dietLogRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final FoodCatalogRepository foodCatalogRepository;
    private final UserRepository userRepository;

    private final Counter dietLogCreatedCounter;

    public DietLogService(DietLogRepository dietLogRepository,
                          FoodEntryRepository foodEntryRepository,
                          FoodCatalogRepository foodCatalogRepository,
                          UserRepository userRepository,
                          MeterRegistry meterRegistry) {
        this.dietLogRepository = dietLogRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.foodCatalogRepository = foodCatalogRepository;
        this.userRepository = userRepository;
        this.dietLogCreatedCounter = Counter.builder("healthcare.diet.log.created")
            .description("식단 기록 생성 수")
            .register(meterRegistry);
    }

    // ─────────────────────────── 식사 기록 생성 ───────────────────────────

    @Transactional
    public CreateDietLogResponse createDietLog(Long userId, CreateDietLogRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Map<Long, FoodCatalog> catalogMap = loadAndValidateCatalogs(userId, request.getEntries());

        Aggregation agg = buildEntries(request.getEntries(), catalogMap, /* dietLogId */ null);

        DietLog log = DietLog.builder()
                .userId(userId)
                .logDate(request.getLogDate())
                .mealType(request.getMealType())
                .totalCalories(agg.totals().totalCalories())
                .totalProteinG(agg.totals().totalProteinG())
                .totalCarbsG(agg.totals().totalCarbsG())
                .totalFatG(agg.totals().totalFatG())
                .totalSugarsG(agg.totals().totalSugarsG())
                .totalDietaryFiberG(agg.totals().totalDietaryFiberG())
                .totalSaturatedFatG(agg.totals().totalSaturatedFatG())
                .totalTransFatG(agg.totals().totalTransFatG())
                .totalCholesterolMg(agg.totals().totalCholesterolMg())
                .totalSodiumMg(agg.totals().totalSodiumMg())
                .notes(request.getNotes())
                .build();
        DietLog savedLog = dietLogRepository.save(log);

        List<FoodEntry> entriesWithLogId = agg.entries().stream()
                .map(e -> rebuildWithLogId(e, savedLog.getId()))
                .toList();
        foodEntryRepository.saveAll(entriesWithLogId);

        request.getEntries().stream()
                .map(CreateFoodEntryRequest::getFoodCatalogId)
                .forEach(foodCatalogRepository::incrementUsageCount);

        dietLogCreatedCounter.increment();
        return toCreateResponse(savedLog, agg.entries().size());
    }

    // ─────────────────────────── 식사 기록 단건 조회 ───────────────────────────

    public DietLogDetailResponse getDietLogById(Long userId, Long logId) {
        DietLog log = dietLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("DietLog", logId));

        if (!log.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식단 기록에 접근할 수 없습니다.");
        }

        List<FoodEntry> rawEntries = foodEntryRepository.findByDietLogIdOrderById(logId);
        List<Long> catalogIds = rawEntries.stream()
                .map(FoodEntry::getFoodCatalogId)
                .distinct()
                .toList();
        Map<Long, FoodCatalog> catalogMap = foodCatalogRepository.findAllById(catalogIds).stream()
                .collect(Collectors.toMap(FoodCatalog::getId, c -> c));

        List<FoodEntryResponse> entryResponses = rawEntries.stream()
                .map(e -> {
                    FoodCatalog food = catalogMap.get(e.getFoodCatalogId());
                    String name   = food != null ? food.getName() : null;
                    String nameKo = food != null ? food.getNameKo() : null;
                    var category  = food != null ? food.getCategory() : null;
                    return FoodEntryResponse.from(e, name, nameKo, category);
                })
                .toList();

        return DietLogDetailResponse.from(log, entryResponses);
    }

    // ─────────────────────────── 식사 기록 목록 조회 ───────────────────────────

    public DietLogListResponse listDietLogs(Long userId, LocalDate from, LocalDate to,
            Pageable pageable) {
        // PostgreSQL nullable 파라미터 타입 추론 불가 문제 회피
        // — null이면 합리적인 기본 범위를 사용한다.
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now().plusYears(1);
        Page<DietLog> page = dietLogRepository.findByUserIdAndDateRange(
                userId, effectiveFrom, effectiveTo, pageable);
        return DietLogListResponse.from(page);
    }

    // ─────────────────────────── 식사 기록 수정 ───────────────────────────

    @Transactional
    public CreateDietLogResponse updateDietLog(Long userId, Long logId, UpdateDietLogRequest request) {
        DietLog log = dietLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("DietLog", logId));

        if (!log.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식단 기록을 수정할 수 없습니다.");
        }

        List<FoodEntry> oldEntries = foodEntryRepository.findByDietLogIdOrderById(logId);
        oldEntries.stream()
                .map(FoodEntry::getFoodCatalogId)
                .distinct()
                .forEach(foodCatalogRepository::decrementUsageCount);
        foodEntryRepository.deleteAll(oldEntries);

        Map<Long, FoodCatalog> catalogMap = loadAndValidateCatalogs(userId, request.getEntries());
        Aggregation agg = buildEntries(request.getEntries(), catalogMap, logId);

        foodEntryRepository.saveAll(agg.entries());
        request.getEntries().stream()
                .map(CreateFoodEntryRequest::getFoodCatalogId)
                .forEach(foodCatalogRepository::incrementUsageCount);

        log.update(request.getMealType(), request.getLogDate(), request.getNotes(), agg.totals());
        dietLogRepository.save(log);

        return toCreateResponse(log, agg.entries().size());
    }

    // ─────────────────────────── 식사 기록 삭제 (소프트) ───────────────────────────

    @Transactional
    public void deleteDietLog(Long userId, Long logId) {
        DietLog log = dietLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("DietLog", logId));

        if (!log.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식단 기록을 삭제할 수 없습니다.");
        }

        foodEntryRepository.findByDietLogIdOrderById(logId).stream()
                .map(FoodEntry::getFoodCatalogId)
                .distinct()
                .forEach(foodCatalogRepository::decrementUsageCount);

        log.softDelete();
        dietLogRepository.save(log);
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    /** 영양소 10종을 servingG/100g 비율로 환산하고 FoodEntry 목록과 합산값을 함께 반환한다. */
    private Aggregation buildEntries(List<CreateFoodEntryRequest> entryRequests,
                                     Map<Long, FoodCatalog> catalogMap,
                                     Long dietLogId) {
        List<FoodEntry> entries = new ArrayList<>(entryRequests.size());
        double cal = 0, prot = 0, carbs = 0, fat = 0;
        double sugars = 0, fiber = 0, satFat = 0, transFat = 0, chol = 0, sodium = 0;

        for (CreateFoodEntryRequest req : entryRequests) {
            FoodCatalog food = catalogMap.get(req.getFoodCatalogId());
            double factor = req.getServingG() / 100.0;

            double calories      = round(food.getCaloriesPer100g() * factor);
            double proteinG      = round(orZero(food.getProteinPer100g()) * factor);
            double carbsG        = round(orZero(food.getCarbsPer100g()) * factor);
            double fatG          = round(orZero(food.getFatPer100g()) * factor);
            double sugarsG       = round(orZero(food.getSugarsPer100g()) * factor);
            double dietaryFiberG = round(orZero(food.getDietaryFiberPer100g()) * factor);
            double saturatedFatG = round(orZero(food.getSaturatedFatPer100g()) * factor);
            double transFatG     = round(orZero(food.getTransFatPer100g()) * factor);
            double cholesterolMg = round(orZero(food.getCholesterolPer100gMg()) * factor);
            double sodiumMg      = round(orZero(food.getSodiumPer100gMg()) * factor);

            cal += calories;
            prot += proteinG;
            carbs += carbsG;
            fat += fatG;
            sugars += sugarsG;
            fiber += dietaryFiberG;
            satFat += saturatedFatG;
            transFat += transFatG;
            chol += cholesterolMg;
            sodium += sodiumMg;

            entries.add(FoodEntry.builder()
                    .dietLogId(dietLogId)
                    .foodCatalogId(req.getFoodCatalogId())
                    .servingG(req.getServingG())
                    .calories(calories)
                    .proteinG(proteinG)
                    .carbsG(carbsG)
                    .fatG(fatG)
                    .sugarsG(sugarsG)
                    .dietaryFiberG(dietaryFiberG)
                    .saturatedFatG(saturatedFatG)
                    .transFatG(transFatG)
                    .cholesterolMg(cholesterolMg)
                    .sodiumMg(sodiumMg)
                    .notes(req.getNotes())
                    .build());
        }

        DietLogNutritionTotals totals = new DietLogNutritionTotals(
                round(cal), round(carbs), round(sugars), round(fiber),
                round(prot), round(fat), round(satFat), round(transFat),
                round(chol), round(sodium));
        return new Aggregation(entries, totals);
    }

    /** dietLogId가 null이었던 FoodEntry에 dietLogId만 채워서 재빌드한다. */
    private FoodEntry rebuildWithLogId(FoodEntry e, Long dietLogId) {
        return FoodEntry.builder()
                .dietLogId(dietLogId)
                .foodCatalogId(e.getFoodCatalogId())
                .servingG(e.getServingG())
                .calories(e.getCalories())
                .proteinG(e.getProteinG())
                .carbsG(e.getCarbsG())
                .fatG(e.getFatG())
                .sugarsG(e.getSugarsG())
                .dietaryFiberG(e.getDietaryFiberG())
                .saturatedFatG(e.getSaturatedFatG())
                .transFatG(e.getTransFatG())
                .cholesterolMg(e.getCholesterolMg())
                .sodiumMg(e.getSodiumMg())
                .notes(e.getNotes())
                .build();
    }

    private CreateDietLogResponse toCreateResponse(DietLog log, int entryCount) {
        return CreateDietLogResponse.builder()
                .dietLogId(log.getId())
                .logDate(log.getLogDate())
                .mealType(log.getMealType())
                .entryCount(entryCount)
                .totalCalories(log.getTotalCalories())
                .totalProteinG(log.getTotalProteinG())
                .totalCarbsG(log.getTotalCarbsG())
                .totalFatG(log.getTotalFatG())
                .totalSugarsG(log.getTotalSugarsG())
                .totalDietaryFiberG(log.getTotalDietaryFiberG())
                .totalSaturatedFatG(log.getTotalSaturatedFatG())
                .totalTransFatG(log.getTotalTransFatG())
                .totalCholesterolMg(log.getTotalCholesterolMg())
                .totalSodiumMg(log.getTotalSodiumMg())
                .build();
    }

    private Map<Long, FoodCatalog> loadAndValidateCatalogs(Long userId,
            List<CreateFoodEntryRequest> entries) {
        List<Long> catalogIds = entries.stream()
                .map(CreateFoodEntryRequest::getFoodCatalogId)
                .distinct()
                .toList();

        Map<Long, FoodCatalog> catalogMap = new java.util.LinkedHashMap<>();
        for (Long id : catalogIds) {
            FoodCatalog food = foodCatalogRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("FoodCatalog", id));
            catalogMap.put(id, food);
        }
        return catalogMap;
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Aggregation(List<FoodEntry> entries, DietLogNutritionTotals totals) {}
}
