package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알러젠 신뢰 레벨 판정 게이트.
 * 식품-알러젠 태그 맵을 받아 추천 적합 여부를 판단한다.
 */
@Component
public class AllergenConfidenceGate {

    /**
     * 식품이 제한된 알러젠을 포함하는지 확인한다.
     * @return true = 알러젠 포함 (→ 추천 후보에서 제외)
     */
    public boolean containsAllergen(
            Long foodId,
            Set<AllergenTag> restrictedTags,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId
    ) {
        if (restrictedTags.isEmpty()) return false;
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        return tags.stream().anyMatch(t -> restrictedTags.contains(t.getAllergenTag()));
    }

    /**
     * Strict 모드 신뢰 레벨 게이트 통과 여부를 반환한다.
     * <ul>
     *   <li>제한 알러젠 없음 / 기본 모드: 항상 통과</li>
     *   <li>Strict 모드: DIRECT_VERIFIED 또는 LABEL_DERIVED 레코드가 최소 하나 있어야 통과</li>
     * </ul>
     */
    public boolean passesGate(
            Long foodId,
            Set<AllergenTag> restrictedTags,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId,
            boolean strictMode
    ) {
        if (restrictedTags.isEmpty()) return true;
        if (!strictMode) return true;
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        return tags.stream().anyMatch(t ->
                t.getConfidenceLevel() == AllergenConfidenceLevel.DIRECT_VERIFIED ||
                t.getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED);
    }

    /**
     * 식품의 알러젠 검토 신뢰 레벨을 결정한다 (응답 표시용).
     * 여러 레코드가 있으면 가장 높은 레벨을 반환한다.
     */
    public AllergenConfidenceLevel resolveConfidence(
            Long foodId,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId
    ) {
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        if (tags.isEmpty()) return AllergenConfidenceLevel.UNKNOWN;
        return tags.stream()
                .map(FoodAllergenTag::getConfidenceLevel)
                .min(Comparator.comparingInt(AllergenConfidenceLevel::ordinal))
                .orElse(AllergenConfidenceLevel.UNKNOWN);
    }
}
