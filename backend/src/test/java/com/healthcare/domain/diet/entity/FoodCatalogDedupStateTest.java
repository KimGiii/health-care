package com.healthcare.domain.diet.entity;

import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FoodCatalog — dedup 상태 전이")
class FoodCatalogDedupStateTest {

    @Test
    @DisplayName("기본 상태는 CANONICAL이며 대표 행이다")
    void defaultIsCanonical() {
        FoodCatalog food = food(1L);

        assertThat(food.getDedupState()).isEqualTo(DedupState.CANONICAL);
        assertThat(food.isCanonicalRow()).isTrue();
    }

    @Test
    @DisplayName("assignDedupKeys는 클러스터 키를 부여한다")
    void assignDedupKeysSetsCluster() {
        FoodCatalog food = food(1L);

        food.assignDedupKeys("D001", "김치찌개");

        assertThat(food.getDedupGroup()).isEqualTo("D001");
        assertThat(food.getDedupNameKey()).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("supersedeBy는 대표를 가리키고 SUPERSEDED로 강등한다")
    void supersedeByDemotes() {
        FoodCatalog canonical = food(10L);
        FoodCatalog loser = food(20L);

        loser.supersedeBy(canonical);

        assertThat(loser.getDedupState()).isEqualTo(DedupState.SUPERSEDED);
        assertThat(loser.getCanonicalGroupId()).isEqualTo(10L);
        assertThat(loser.isCanonicalRow()).isFalse();
    }

    @Test
    @DisplayName("영속화되지 않은 대표로 supersede하면 예외를 던진다")
    void supersedeByRequiresPersistedCanonical() {
        FoodCatalog canonical = food(null);
        FoodCatalog loser = food(20L);

        assertThatThrownBy(() -> loser.supersedeBy(canonical))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markCanonical은 supersede 포인터를 끊고 대표로 복귀시킨다")
    void markCanonicalRestoresRepresentative() {
        FoodCatalog food = food(20L);
        food.supersedeBy(food(10L));

        food.markCanonical();

        assertThat(food.getDedupState()).isEqualTo(DedupState.CANONICAL);
        assertThat(food.isCanonicalRow()).isTrue();
        assertThat(food.getCanonicalGroupId()).isNull();
    }

    @Test
    @DisplayName("markCollision은 대표 지위를 유지한 채 검토 큐로만 표시한다")
    void markCollisionKeepsRepresentative() {
        FoodCatalog food = food(1L);

        food.markCollision();

        assertThat(food.getDedupState()).isEqualTo(DedupState.COLLISION);
        assertThat(food.isCanonicalRow()).isTrue();
    }

    private FoodCatalog food(Long id) {
        return FoodCatalog.builder()
                .id(id)
                .name("식품")
                .nameKo("식품")
                .category(FoodCategory.OTHER)
                .caloriesPer100g(100.0)
                .source(FoodCatalogSource.MFDS_STANDARD_DISH)
                .isCustom(false)
                .build();
    }
}
