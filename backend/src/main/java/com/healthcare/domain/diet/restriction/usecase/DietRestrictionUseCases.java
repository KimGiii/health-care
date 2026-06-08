package com.healthcare.domain.diet.restriction.usecase;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.restriction.dto.CreateDietRestrictionRequest;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DietRestrictionUseCases {

    private final DietRestrictionRepository dietRestrictionRepository;
    private final FoodCatalogRepository foodCatalogRepository;

    public List<DietRestrictionResponse> listRestrictions(Long userId) {
        return dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(DietRestrictionResponse::from)
                .toList();
    }

    @Transactional
    public DietRestrictionResponse createRestriction(Long userId, CreateDietRestrictionRequest request) {
        String normalizedKeyword = request.getTargetType() == TargetType.KEYWORD
                ? normalizeKeyword(request.getKeyword()) : null;
        validateAndCheckDuplicate(userId, request, normalizedKeyword);

        DietRestriction restriction = DietRestriction.builder()
                .userId(userId)
                .restrictionType(request.getRestrictionType())
                .targetType(request.getTargetType())
                .foodCatalogId(request.getFoodCatalogId())
                .category(request.getCategory())
                .keyword(normalizedKeyword)
                .allergenTag(request.getAllergenTag())
                .build();

        return DietRestrictionResponse.from(dietRestrictionRepository.save(restriction));
    }

    @Transactional
    public void deleteRestriction(Long userId, Long restrictionId) {
        DietRestriction restriction = dietRestrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new ResourceNotFoundException("DietRestriction", restrictionId));

        if (!restriction.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 식단 제한 조건을 삭제할 수 없습니다.");
        }

        restriction.softDelete();
        dietRestrictionRepository.save(restriction);
    }

    // ─── 내부 헬퍼 ───

    private void validateAndCheckDuplicate(Long userId, CreateDietRestrictionRequest request, String normalizedKeyword) {
        switch (request.getTargetType()) {
            case FOOD -> {
                if (request.getFoodCatalogId() == null)
                    throw new IllegalArgumentException("FOOD 제한에는 foodCatalogId가 필요합니다.");
                foodCatalogRepository.findById(request.getFoodCatalogId())
                        .orElseThrow(() -> new ResourceNotFoundException("FoodCatalog", request.getFoodCatalogId()));
                if (dietRestrictionRepository.findActiveFoodRestriction(userId, request.getRestrictionType(), request.getFoodCatalogId()).isPresent())
                    throw new DuplicateResourceException("이미 등록된 식단 제한 조건입니다.");
            }
            case CATEGORY -> {
                if (request.getCategory() == null)
                    throw new IllegalArgumentException("CATEGORY 제한에는 category가 필요합니다.");
                if (dietRestrictionRepository.findActiveCategoryRestriction(userId, request.getRestrictionType(), request.getCategory()).isPresent())
                    throw new DuplicateResourceException("이미 등록된 식단 제한 조건입니다.");
            }
            case KEYWORD -> {
                if (request.getKeyword() == null || request.getKeyword().isBlank())
                    throw new IllegalArgumentException("KEYWORD 제한에는 1자 이상의 keyword가 필요합니다.");
                if (normalizedKeyword.isEmpty() || normalizedKeyword.length() > 100)
                    throw new IllegalArgumentException("keyword는 1~100자여야 합니다.");
                if (dietRestrictionRepository.findActiveKeywordRestriction(userId, request.getRestrictionType(), normalizedKeyword).isPresent())
                    throw new DuplicateResourceException("이미 등록된 식단 제한 조건입니다.");
            }
            case ALLERGEN_TAG -> {
                if (request.getAllergenTag() == null)
                    throw new IllegalArgumentException("ALLERGEN_TAG 제한에는 allergenTag가 필요합니다.");
                if (dietRestrictionRepository.findActiveAllergenTagRestriction(userId, request.getRestrictionType(), request.getAllergenTag()).isPresent())
                    throw new DuplicateResourceException("이미 등록된 식단 제한 조건입니다.");
            }
        }
    }

    /** 키워드 정규화: NFC 정규화, 연속 공백 축약, 앞뒤 공백 제거 */
    static String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        return Normalizer.normalize(keyword, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
