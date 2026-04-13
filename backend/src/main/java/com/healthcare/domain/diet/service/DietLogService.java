package com.healthcare.domain.diet.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodEntry;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodEntryRepository;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DietLogService {

    private final DietLogRepository dietLogRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final FoodCatalogRepository foodCatalogRepository;
    private final UserRepository userRepository;

    // ─────────────────────────── 식사 기록 생성 ───────────────────────────

    @Transactional
    public CreateDietLogResponse createDietLog(Long userId, CreateDietLogRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // 식품 카탈로그 로드 및 접근 검증
        Map<Long, FoodCatalog> catalogMap = loadAndValidateCatalogs(userId, request.getEntries());

        // 영양소 합산 + FoodEntry 생성
        List<FoodEntry> entries = new ArrayList<>();
        double totalCalories = 0.0;
        double totalProteinG = 0.0;
        double totalCarbsG   = 0.0;
        double totalFatG     = 0.0;

        for (CreateFoodEntryRequest entryReq : request.getEntries()) {
            FoodCatalog food = catalogMap.get(entryReq.getFoodCatalogId());
            double factor = entryReq.getServingG() / 100.0;

            double calories = round(food.getCaloriesPer100g() * factor);
            double proteinG = round(orZero(food.getProteinPer100g()) * factor);
            double carbsG   = round(orZero(food.getCarbsPer100g()) * factor);
            double fatG     = round(orZero(food.getFatPer100g()) * factor);

            totalCalories += calories;
            totalProteinG += proteinG;
            totalCarbsG   += carbsG;
            totalFatG     += fatG;

            entries.add(FoodEntry.builder()
                    .dietLogId(null) // 로그 저장 후 채움
                    .foodCatalogId(entryReq.getFoodCatalogId())
                    .servingG(entryReq.getServingG())
                    .calories(calories)
                    .proteinG(proteinG)
                    .carbsG(carbsG)
                    .fatG(fatG)
                    .notes(entryReq.getNotes())
                    .build());
        }

        // 식사 기록 저장
        DietLog log = DietLog.builder()
                .userId(userId)
                .logDate(request.getLogDate())
                .mealType(request.getMealType())
                .totalCalories(round(totalCalories))
                .totalProteinG(round(totalProteinG))
                .totalCarbsG(round(totalCarbsG))
                .totalFatG(round(totalFatG))
                .notes(request.getNotes())
                .build();
        DietLog savedLog = dietLogRepository.save(log);

        // 식품 항목에 dietLogId 할당 후 저장
        List<FoodEntry> entriesWithLogId = entries.stream()
                .map(e -> FoodEntry.builder()
                        .dietLogId(savedLog.getId())
                        .foodCatalogId(e.getFoodCatalogId())
                        .servingG(e.getServingG())
                        .calories(e.getCalories())
                        .proteinG(e.getProteinG())
                        .carbsG(e.getCarbsG())
                        .fatG(e.getFatG())
                        .notes(e.getNotes())
                        .build())
                .toList();
        foodEntryRepository.saveAll(entriesWithLogId);

        return CreateDietLogResponse.builder()
                .dietLogId(savedLog.getId())
                .logDate(savedLog.getLogDate())
                .mealType(savedLog.getMealType())
                .entryCount(entries.size())
                .totalCalories(savedLog.getTotalCalories())
                .totalProteinG(savedLog.getTotalProteinG())
                .totalCarbsG(savedLog.getTotalCarbsG())
                .totalFatG(savedLog.getTotalFatG())
                .build();
    }

    // ─────────────────────────── 식사 기록 단건 조회 ───────────────────────────

    public DietLogDetailResponse getDietLogById(Long userId, Long logId) {
        DietLog log = dietLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("DietLog", logId));

        if (!log.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식사 기록에 접근할 수 없습니다.");
        }

        List<FoodEntry> rawEntries = foodEntryRepository.findByDietLogIdOrderById(logId);
        Map<Long, FoodCatalog> catalogMap = rawEntries.stream()
                .map(FoodEntry::getFoodCatalogId)
                .distinct()
                .flatMap(cid -> foodCatalogRepository.findById(cid).stream())
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
        Page<DietLog> page = dietLogRepository.findByUserIdAndDateRange(userId, from, to, pageable);
        return DietLogListResponse.from(page);
    }

    // ─────────────────────────── 식사 기록 삭제 (소프트) ───────────────────────────

    @Transactional
    public void deleteDietLog(Long userId, Long logId) {
        DietLog log = dietLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("DietLog", logId));

        if (!log.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식사 기록을 삭제할 수 없습니다.");
        }

        log.softDelete();
        dietLogRepository.save(log);
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

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
            // 다른 사용자의 커스텀 식품은 접근 불가
            if (Boolean.TRUE.equals(food.getIsCustom())
                    && !userId.equals(food.getCreatedByUserId())) {
                throw new ResourceNotFoundException("FoodCatalog", id);
            }
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
}
