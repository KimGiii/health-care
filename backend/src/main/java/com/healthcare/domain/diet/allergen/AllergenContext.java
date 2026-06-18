package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;

import java.util.List;
import java.util.Set;

/**
 * 식품 하나의 알러젠 안전 판단에 필요한 입력.
 * profile은 FoodAllergenProfile 레코드가 없을 때 null이다.
 */
public record AllergenContext(
        Long foodId,
        Set<AllergenTag> restrictedTags,
        List<FoodAllergenTag> tags,
        FoodAllergenProfile profile,
        boolean strictAllergyMode
) {}
