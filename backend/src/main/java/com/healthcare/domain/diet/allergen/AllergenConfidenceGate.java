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
     * <p>
     * Positive allergen tag 모델에서는 "태그가 없음"만으로 제한 알러젠 부재를 증명할 수 없다.
     * Strict 모드에서는 포함 알러젠 태그를 먼저 제외한 뒤, 해당 식품의 알러젠 프로필 전체가
     * 고신뢰 출처로 검토되었음을 나타내는 레코드가 있을 때만 통과시킨다.
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
                t.isAllergenProfileVerified() && (
                        t.getConfidenceLevel() == AllergenConfidenceLevel.DIRECT_VERIFIED ||
                        t.getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED
                ));
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
