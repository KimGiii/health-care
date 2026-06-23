package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("FoodCatalogCanonicalBackfillService — 기존 DB canonical 정규화 백필")
class FoodCatalogCanonicalBackfillServiceTest {

    private Map<Long, FoodCatalog> db;
    private FoodCatalogRepository catalogRepository;
    private FoodServingOptionRepository servingOptionRepository;
    private FoodCatalogCanonicalBackfillService service;

    @BeforeEach
    void setUp() {
        db = new LinkedHashMap<>();
        catalogRepository = mock(FoodCatalogRepository.class);
        servingOptionRepository = mock(FoodServingOptionRepository.class);

        // 상태형 fake: save/findCanonical/findCanonicalsByGroup 를 인메모리 맵으로 시뮬레이션한다.
        given(catalogRepository.save(any())).willAnswer(inv -> store(inv.getArgument(0)));
        given(catalogRepository.saveAndFlush(any())).willAnswer(inv -> store(inv.getArgument(0)));
        given(catalogRepository.findCanonical(anyString(), anyString())).willAnswer(inv -> {
            enforceCanonicalUnique(); // JPQL auto-flush 모사: 조회 전 부분 유니크 인덱스 위반 검증
            String group = inv.getArgument(0);
            String nameKey = inv.getArgument(1);
            return db.values().stream()
                    .filter(f -> group.equals(f.getDedupGroup())
                            && nameKey.equals(f.getDedupNameKey())
                            && f.getCanonicalGroupId() == null)
                    .findFirst();
        });
        given(catalogRepository.findCanonicalsByGroup(anyString())).willAnswer(inv -> {
            enforceCanonicalUnique();
            String group = inv.getArgument(0);
            return db.values().stream()
                    .filter(f -> group.equals(f.getDedupGroup()) && f.getCanonicalGroupId() == null)
                    .toList();
        });
        // id 커서 페이지네이션: afterId 초과 비커스텀 행을 id 순으로 limit 만큼 반환한다.
        given(catalogRepository.findDedupEligibleAfterId(anyLong(), anyInt())).willAnswer(inv -> {
            long afterId = inv.getArgument(0);
            int limit = inv.getArgument(1);
            return db.values().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsCustom()) && f.getId() > afterId)
                    .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                    .limit(limit)
                    .toList();
        });
        given(catalogRepository.countByIsCustomFalseAndDedupState(any())).willAnswer(inv -> {
            DedupState state = inv.getArgument(0);
            return db.values().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsCustom()) && f.getDedupState() == state)
                    .count();
        });
        given(servingOptionRepository.deleteOptionsForSupersededRows()).willAnswer(inv ->
                (int) db.values().stream()
                        .filter(f -> !Boolean.TRUE.equals(f.getIsCustom()) && f.getCanonicalGroupId() != null)
                        .count());

        CanonicalDedupResolver resolver = new CanonicalDedupResolver(catalogRepository);
        EntityManager entityManager = mock(EntityManager.class);
        service = new FoodCatalogCanonicalBackfillService(
                catalogRepository, servingOptionRepository, resolver, entityManager);
    }

    private FoodCatalog store(FoodCatalog food) {
        db.put(food.getId(), food);
        enforceCanonicalUnique();
        return food;
    }

    /**
     * 실제 DB 부분 유니크 인덱스 {@code uq_food_catalog_canonical}
     * ((dedup_group, dedup_name_key) WHERE canonical_group_id IS NULL)을 인메모리로 강제한다.
     */
    private void enforceCanonicalUnique() {
        Map<String, Long> representatives = new HashMap<>();
        for (FoodCatalog f : db.values()) {
            if (f.getCanonicalGroupId() != null || f.getDedupGroup() == null || f.getDedupNameKey() == null) {
                continue;
            }
            String key = f.getDedupGroup() + " " + f.getDedupNameKey();
            Long prev = representatives.put(key, f.getId());
            if (prev != null && !prev.equals(f.getId())) {
                throw new DataIntegrityViolationException(
                        "uq_food_catalog_canonical 위반: (" + f.getDedupGroup() + ", " + f.getDedupNameKey() + ")");
            }
        }
    }

    @Test
    @DisplayName("출처 우선순위로 한 클러스터를 대표 1개·패자 N개로 수렴시킨다")
    void convergesClusterToSingleCanonical() {
        // 같은 코드·이름키 3행이 모두 대표(미백필) 상태로 적재돼 있다.
        store(food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개"));
        store(food(2L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개"));
        store(food(3L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "D001", "김치찌개"));

        FoodCatalogCanonicalBackfillSummary summary = service.backfill();

        // 최고 우선순위(PROCESSED 300)가 대표, 나머지는 패자.
        assertThat(db.get(3L).isCanonicalRow()).isTrue();
        assertThat(db.get(1L).getDedupState()).isEqualTo(DedupState.SUPERSEDED);
        assertThat(db.get(2L).getDedupState()).isEqualTo(DedupState.SUPERSEDED);
        assertThat(summary.processedRows()).isEqualTo(3);
        assertThat(summary.canonicalRows()).isEqualTo(1);
        assertThat(summary.supersededRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("커서 배치 경계를 가로지르는 클러스터도 올바르게 수렴한다(구 대표 강등)")
    void convergesAcrossBatchBoundary() {
        // batchSize=2 이므로 id 1·2 와 3 이 다른 배치에 걸린다.
        store(food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개"));
        store(food(2L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D001", "김치찌개"));
        store(food(3L, FoodCatalogSource.SEED, "D001", "김치찌개"));

        FoodCatalogCanonicalBackfillSummary summary =
                service.backfill(2);

        // SEED 가 최상위라 배치 경계를 넘어 대표로 승격된다.
        assertThat(db.get(3L).isCanonicalRow()).isTrue();
        assertThat(db.get(1L).isCanonicalRow()).isFalse();
        assertThat(db.get(2L).isCanonicalRow()).isFalse();
        assertThat(summary.batchCount()).isGreaterThanOrEqualTo(2);
        assertThat(summary.canonicalRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 코드·다른 이름은 둘 다 대표로 두되 COLLISION으로 표시한다")
    void marksCollision() {
        store(food(1L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X001", "사과"));
        store(food(2L, FoodCatalogSource.MFDS_STANDARD_DISH, "X001", "배"));

        FoodCatalogCanonicalBackfillSummary summary = service.backfill();

        assertThat(db.get(1L).isCanonicalRow()).isTrue();
        assertThat(db.get(2L).isCanonicalRow()).isTrue();
        assertThat(db.get(1L).getDedupState()).isEqualTo(DedupState.COLLISION);
        assertThat(db.get(2L).getDedupState()).isEqualTo(DedupState.COLLISION);
        assertThat(summary.collisionRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("패자 행에 매달린 제공량 옵션을 일괄 정리한다")
    void clearsSupersededServingOptions() {
        store(food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개"));
        store(food(2L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "D001", "김치찌개"));

        FoodCatalogCanonicalBackfillSummary summary = service.backfill();

        // resolve 전량 완료 후 패자 옵션을 일괄 삭제한다(구 대표 누락 방지).
        verify(servingOptionRepository).deleteOptionsForSupersededRows();
        assertThat(summary.servingOptionsCleared()).isEqualTo(1); // id 1 이 패자
    }

    @Test
    @DisplayName("USER_CUSTOM은 dedup 대상이 아니라 커서에서 제외된다")
    void skipsUserCustom() {
        store(food(1L, FoodCatalogSource.USER_CUSTOM, "C001", "내 음식"));
        store(food(2L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개"));

        FoodCatalogCanonicalBackfillSummary summary = service.backfill();

        assertThat(summary.processedRows()).isEqualTo(1);
        assertThat(db.get(1L).getDedupGroup()).isNull();
    }

    @Test
    @DisplayName("재실행해도 대표 구성이 안정적으로 유지된다(멱등)")
    void isIdempotent() {
        store(food(1L, FoodCatalogSource.MFDS_STANDARD_DISH, "D001", "김치찌개"));
        store(food(2L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "D001", "김치찌개"));

        FoodCatalogCanonicalBackfillSummary first = service.backfill();
        FoodCatalogCanonicalBackfillSummary second = service.backfill();

        assertThat(second.canonicalRows()).isEqualTo(first.canonicalRows()).isEqualTo(1);
        assertThat(second.supersededRows()).isEqualTo(first.supersededRows()).isEqualTo(1);
        assertThat(db.get(2L).isCanonicalRow()).isTrue();
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
