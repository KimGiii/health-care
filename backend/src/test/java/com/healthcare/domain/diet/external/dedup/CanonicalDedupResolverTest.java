package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("CanonicalDedupResolver — 출처 우선순위 dedup")
class CanonicalDedupResolverTest {

    private Map<Long, FoodCatalog> db;
    private FoodCatalogRepository repository;
    private CanonicalDedupResolver resolver;

    @BeforeEach
    void setUp() {
        db = new LinkedHashMap<>();
        // 상태형 fake: save/findCanonical/findCanonicalsByGroup 를 인메모리 맵으로 시뮬레이션한다.
        repository = mock(FoodCatalogRepository.class);
        given(repository.save(any())).willAnswer(inv -> store(inv.getArgument(0)));
        given(repository.saveAndFlush(any())).willAnswer(inv -> store(inv.getArgument(0)));
        given(repository.findCanonical(anyString(), anyString())).willAnswer(inv -> {
            String group = inv.getArgument(0);
            String nameKey = inv.getArgument(1);
            return db.values().stream()
                    .filter(f -> group.equals(f.getDedupGroup())
                            && nameKey.equals(f.getDedupNameKey())
                            && f.getCanonicalGroupId() == null)
                    .findFirst();
        });
        given(repository.findCanonicalsByGroup(anyString())).willAnswer(inv -> {
            String group = inv.getArgument(0);
            return db.values().stream()
                    .filter(f -> group.equals(f.getDedupGroup()) && f.getCanonicalGroupId() == null)
                    .toList();
        });
        resolver = new CanonicalDedupResolver(repository);
    }

    private FoodCatalog store(FoodCatalog food) {
        db.put(food.getId(), food);
        return food;
    }

    @Nested
    @DisplayName("우선순위 채택")
    class Priority {

        @Test
        @DisplayName("첫 행은 클러스터 대표(CANONICAL)가 된다")
        void firstRowBecomesCanonical() {
            FoodCatalog dish = food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개");
            store(dish);

            resolver.resolve(dish);

            assertThat(dish.isCanonicalRow()).isTrue();
            assertThat(dish.getDedupState()).isEqualTo(DedupState.CANONICAL);
            assertThat(dish.getDedupGroup()).isEqualTo("D001");
            assertThat(dish.getDedupNameKey()).isNotNull();
        }

        @Test
        @DisplayName("낮은 우선순위 행이 뒤에 오면 패자(SUPERSEDED)로 강등되고 대표를 가리킨다")
        void lowerPriorityArrivalIsSuperseded() {
            FoodCatalog nutrientDb = food(1L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개");
            FoodCatalog dish = food(2L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개");
            store(nutrientDb);
            store(dish);

            resolver.resolve(nutrientDb);
            resolver.resolve(dish);

            assertThat(nutrientDb.isCanonicalRow()).isTrue();
            assertThat(dish.isCanonicalRow()).isFalse();
            assertThat(dish.getDedupState()).isEqualTo(DedupState.SUPERSEDED);
            assertThat(dish.getCanonicalGroupId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("높은 우선순위 행이 뒤에 오면 기존 대표를 강등하고 자신이 대표가 된다")
        void higherPriorityArrivalPromotesItself() {
            FoodCatalog dish = food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개");
            FoodCatalog nutrientDb = food(2L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개");
            store(dish);
            store(nutrientDb);

            resolver.resolve(dish);
            resolver.resolve(nutrientDb);

            assertThat(nutrientDb.isCanonicalRow()).isTrue();
            assertThat(nutrientDb.getDedupState()).isEqualTo(DedupState.CANONICAL);
            assertThat(dish.isCanonicalRow()).isFalse();
            assertThat(dish.getDedupState()).isEqualTo(DedupState.SUPERSEDED);
            assertThat(dish.getCanonicalGroupId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("SEED는 공공데이터보다 우선해 대표를 유지한다")
        void seedOutranksPublicData() {
            FoodCatalog seed = food(1L, FoodCatalogSource.SEED, "D001", "김치찌개");
            FoodCatalog nutrientDb = food(2L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개");
            store(seed);
            store(nutrientDb);

            resolver.resolve(seed);
            resolver.resolve(nutrientDb);

            assertThat(seed.isCanonicalRow()).isTrue();
            assertThat(nutrientDb.isCanonicalRow()).isFalse();
            assertThat(nutrientDb.getCanonicalGroupId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("순서 무관성")
    class OrderIndependence {

        @Test
        @DisplayName("음식→영양DB 와 영양DB→음식 적재가 동일한 대표로 수렴한다")
        void convergesRegardlessOfOrder() {
            // 순서 A: 음식 먼저
            FoodCatalog dishA = food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개");
            FoodCatalog ndbA = food(2L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개");
            store(dishA);
            store(ndbA);
            resolver.resolve(dishA);
            resolver.resolve(ndbA);

            // 순서 B: 영양DB 먼저 (별도 클러스터)
            FoodCatalog ndbB = food(11L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D900", "된장찌개");
            FoodCatalog dishB = food(12L, FoodCatalogSource.MFDS_STANDARD_DISH, "D900", "된장찌개");
            store(ndbB);
            store(dishB);
            resolver.resolve(ndbB);
            resolver.resolve(dishB);

            // 두 순서 모두 영양DB가 대표, 음식은 패자
            assertThat(canonicalOf(ndbA, dishA).getSource()).isEqualTo(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
            assertThat(canonicalOf(ndbB, dishB).getSource()).isEqualTo(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
            assertThat(dishA.getDedupState()).isEqualTo(DedupState.SUPERSEDED);
            assertThat(dishB.getDedupState()).isEqualTo(DedupState.SUPERSEDED);
        }

        private FoodCatalog canonicalOf(FoodCatalog a, FoodCatalog b) {
            return a.isCanonicalRow() ? a : b;
        }
    }

    @Nested
    @DisplayName("충돌 감지")
    class Collision {

        @Test
        @DisplayName("같은 코드·다른 이름은 각자 대표를 유지하되 양쪽 모두 COLLISION으로 표시한다")
        void sameCodeDifferentNameMarksCollision() {
            FoodCatalog apple = food(1L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X001", "사과");
            FoodCatalog pear = food(2L, FoodCatalogSource.MFDS_STANDARD_DISH, "X001", "배");
            store(apple);
            store(pear);

            resolver.resolve(apple);
            resolver.resolve(pear);

            // 이름키가 다르므로 둘 다 대표(노출) 유지
            assertThat(apple.isCanonicalRow()).isTrue();
            assertThat(pear.isCanonicalRow()).isTrue();
            // 그러나 같은 코드 충돌이라 검토 큐로 표시
            assertThat(apple.getDedupState()).isEqualTo(DedupState.COLLISION);
            assertThat(pear.getDedupState()).isEqualTo(DedupState.COLLISION);
        }
    }

    @Nested
    @DisplayName("dedup 비대상·멱등성")
    class Skips {

        @Test
        @DisplayName("USER_CUSTOM은 dedup 대상이 아니라 키를 부여하지 않는다")
        void userCustomIsSkipped() {
            FoodCatalog custom = food(1L, FoodCatalogSource.USER_CUSTOM, "C001", "내 음식");

            resolver.resolve(custom);

            assertThat(custom.getDedupGroup()).isNull();
            assertThat(custom.getDedupState()).isEqualTo(DedupState.CANONICAL);
        }

        @Test
        @DisplayName("food_code가 없으면 클러스터링하지 않는다")
        void rowWithoutCodeIsSkipped() {
            FoodCatalog noCode = food(1L, FoodCatalogSource.SEED, null, "코드없는 식품");

            resolver.resolve(noCode);

            assertThat(noCode.getDedupGroup()).isNull();
            assertThat(noCode.isCanonicalRow()).isTrue();
        }

        @Test
        @DisplayName("동일 행 재적재(재호출)는 대표 지위를 안정적으로 유지한다(멱등)")
        void reResolveIsIdempotent() {
            FoodCatalog dish = food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개");
            store(dish);

            resolver.resolve(dish);
            resolver.resolve(dish);

            assertThat(dish.isCanonicalRow()).isTrue();
            assertThat(dish.getDedupState()).isEqualTo(DedupState.CANONICAL);
            assertThat(canonicalCount("D001")).isEqualTo(1);
        }

        private long canonicalCount(String group) {
            return db.values().stream()
                    .filter(f -> group.equals(f.getDedupGroup()) && f.getCanonicalGroupId() == null)
                    .count();
        }
    }

    private FoodCatalog food(long id, FoodCatalogSource source, String code, String nameKo) {
        return FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode(code)
                .name(nameKo)
                .nameKo(nameKo)
                .category(FoodCategory.OTHER)
                .caloriesPer100g(100.0)
                .isCustom(source == FoodCatalogSource.USER_CUSTOM)
                .build();
    }
}
