package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;

/**
 * 충돌(COLLISION) 검토 큐의 멤버 한 행. 같은 코드를 공유하지만 이름키가 달라 각자 대표로 남은
 * canonical 행을 운영자가 비교·해소할 수 있도록 식별 정보와 영양 핵심만 노출한다.
 */
public record DedupCollisionMember(
        Long id,
        String source,
        String foodCode,
        String nameKo,
        String dedupNameKey,
        Double caloriesPer100g
) {
    public static DedupCollisionMember from(FoodCatalog food) {
        return new DedupCollisionMember(
                food.getId(),
                food.getSource() != null ? food.getSource().name() : null,
                food.getFoodCode(),
                food.getNameKo() != null ? food.getNameKo() : food.getName(),
                food.getDedupNameKey(),
                food.getCaloriesPer100g()
        );
    }
}
