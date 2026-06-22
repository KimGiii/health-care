package com.healthcare.domain.diet.allergen;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.allergen.dto.AddAllergenTagRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAddAllergenTagsRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAllergenTagResult;
import com.healthcare.domain.diet.allergen.dto.FoodAllergenTagResponse;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodAllergenTagAdminOperations {

    private final AdminOperationGuard adminOperationGuard;
    private final FoodAllergenTagRepository tagRepository;
    private final FoodCatalogRepository foodCatalogRepository;

    @Transactional
    public FoodAllergenTagResponse add(String adminToken, AddAllergenTagRequest request) {
        adminOperationGuard.assertAllowed(adminToken);
        validate(request);
        assertFoodExists(request.foodCatalogId());

        if (tagRepository.existsByFoodCatalogIdAndAllergenTag(request.foodCatalogId(), request.allergenTag())) {
            FoodAllergenTag existing = tagRepository
                    .findByFoodCatalogId(request.foodCatalogId())
                    .stream()
                    .filter(t -> t.getAllergenTag() == request.allergenTag())
                    .findFirst()
                    .orElseThrow();
            log.info("알러젠 태그 이미 존재 — 기존 반환: foodId={}, tag={}", request.foodCatalogId(), request.allergenTag());
            return FoodAllergenTagResponse.from(existing);
        }

        FoodAllergenTag saved = tagRepository.save(toEntity(request));
        log.info("알러젠 태그 추가: foodId={}, tag={}, confidence={}", saved.getFoodCatalogId(), saved.getAllergenTag(), saved.getConfidenceLevel());
        return FoodAllergenTagResponse.from(saved);
    }

    @Transactional
    public BulkAllergenTagResult bulkAdd(String adminToken, BulkAddAllergenTagsRequest request) {
        adminOperationGuard.assertAllowed(adminToken);
        int created = 0;
        int skipped = 0;
        for (AddAllergenTagRequest item : request.tags()) {
            validate(item);
            if (!foodCatalogRepository.existsById(item.foodCatalogId())) {
                log.warn("알러젠 태그 벌크 추가 — 식품 없음, 스킵: foodId={}", item.foodCatalogId());
                skipped++;
                continue;
            }
            if (tagRepository.existsByFoodCatalogIdAndAllergenTag(item.foodCatalogId(), item.allergenTag())) {
                skipped++;
                continue;
            }
            tagRepository.save(toEntity(item));
            created++;
        }
        log.info("알러젠 태그 벌크 추가 완료: created={}, skipped={}", created, skipped);
        return new BulkAllergenTagResult(created, skipped);
    }

    @Transactional(readOnly = true)
    public List<FoodAllergenTagResponse> getByFoodId(String adminToken, Long foodCatalogId) {
        adminOperationGuard.assertAllowed(adminToken);
        assertFoodExists(foodCatalogId);
        return tagRepository.findByFoodCatalogId(foodCatalogId)
                .stream()
                .map(FoodAllergenTagResponse::from)
                .toList();
    }

    @Transactional
    public void delete(String adminToken, Long tagId) {
        adminOperationGuard.assertAllowed(adminToken);
        if (!tagRepository.existsById(tagId)) {
            throw new ResourceNotFoundException("알러젠 태그를 찾을 수 없습니다: id=" + tagId);
        }
        tagRepository.deleteById(tagId);
        log.info("알러젠 태그 삭제: tagId={}", tagId);
    }

    private void validate(AddAllergenTagRequest request) {
        if (request.foodCatalogId() == null) {
            throw new ValidationException("foodCatalogId는 필수입니다.");
        }
        if (request.allergenTag() == null) {
            throw new ValidationException("allergenTag는 필수입니다.");
        }
        if (request.confidenceLevel() == null) {
            throw new ValidationException("confidenceLevel은 필수입니다.");
        }
        if (request.source() == null) {
            throw new ValidationException("source는 필수입니다.");
        }
        if (Boolean.TRUE.equals(request.allergenProfileVerified())
                && !request.confidenceLevel().isProfileGrade()) {
            throw new ValidationException(
                    "allergenProfileVerified는 DIRECT_VERIFIED 또는 LABEL_DERIVED 태그에만 사용할 수 있습니다.");
        }
    }

    private void assertFoodExists(Long foodCatalogId) {
        if (!foodCatalogRepository.existsById(foodCatalogId)) {
            throw new ResourceNotFoundException("식품 카탈로그를 찾을 수 없습니다: id=" + foodCatalogId);
        }
    }

    private FoodAllergenTag toEntity(AddAllergenTagRequest request) {
        return FoodAllergenTag.builder()
                .foodCatalogId(request.foodCatalogId())
                .allergenTag(request.allergenTag())
                .confidenceLevel(request.confidenceLevel())
                .source(request.source())
                .allergenProfileVerified(Boolean.TRUE.equals(request.allergenProfileVerified()))
                .reviewedAt(request.reviewedAt())
                .build();
    }
}
