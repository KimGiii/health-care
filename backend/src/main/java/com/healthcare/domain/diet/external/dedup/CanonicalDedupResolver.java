package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.identity.FoodCatalogIdentity;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 출처 우선순위 dedup의 행별 정규화 로직(설계 §4). 적재·재적재 시 이미 영속화된 행 하나를 받아
 * 같은 코드·이름키 클러스터의 대표(canonical)를 출처 우선순위로 결정한다.
 *
 * <p>항상 우선순위를 비교해 승격/강등하므로 <b>출처 적재 순서와 무관하게 동일 결과로 수렴</b>한다.
 * 대표 = {@code canonical_group_id IS NULL}(V35 재사용). 패자는 supersede 포인터로 대표를 가리키며
 * 추천·검색에서 제외된다.
 */
@Component
@RequiredArgsConstructor
public class CanonicalDedupResolver {

    private final FoodCatalogRepository foodCatalogRepository;

    /**
     * 영속화된 {@code row}의 정규 위치를 결정한다.
     * 코드 없는 행·USER_CUSTOM은 dedup 비대상이라 대표(standalone)로 둔다.
     */
    public void resolve(FoodCatalog row) {
        if (!row.getSource().isDedupEligible()) {
            return;
        }
        String group = row.getFoodCode();
        if (group == null || group.isBlank()) {
            return; // 코드 없음 → 클러스터링 불가, 기존 대표 지위 유지
        }
        String nameKey = FoodCatalogIdentity.duplicateNameKey(displayName(row));
        row.assignDedupKeys(group, nameKey);
        if (nameKey == null) {
            row.markCanonical();
            foodCatalogRepository.save(row);
            return; // 이름키 없음 → 신뢰할 클러스터 키 부재, standalone 대표
        }

        FoodCatalog canonical = decideCanonical(row, group, nameKey);
        detectCollision(canonical, group, nameKey);
    }

    /** §4-2: 클러스터 대표를 우선순위로 결정하고, 결정된 대표 행을 반환한다. */
    private FoodCatalog decideCanonical(FoodCatalog row, String group, String nameKey) {
        FoodCatalog canonical = foodCatalogRepository.findCanonical(group, nameKey).orElse(null);

        if (canonical == null || canonical.getId().equals(row.getId())) {
            row.markCanonical();
            return foodCatalogRepository.save(row);
        }

        if (row.getSource().dedupPriority() > canonical.getSource().dedupPriority()) {
            // 구 대표를 먼저 강등·flush 한 뒤 신규를 승격한다.
            // (부분 유니크 인덱스 uq_food_catalog_canonical 의 일시적 중복 위반 방지)
            canonical.supersedeBy(row);
            foodCatalogRepository.saveAndFlush(canonical);
            row.markCanonical();
            return foodCatalogRepository.save(row);
        }

        row.supersedeBy(canonical);
        foodCatalogRepository.save(row);
        return canonical;
    }

    /** §4-3: 같은 코드·다른 이름키의 대표가 공존하면 양쪽을 충돌(검토 큐)로 표시한다. */
    private void detectCollision(FoodCatalog canonical, String group, String nameKey) {
        List<FoodCatalog> others = foodCatalogRepository.findCanonicalsByGroup(group).stream()
                .filter(other -> !other.getId().equals(canonical.getId()))
                .filter(other -> !Objects.equals(other.getDedupNameKey(), nameKey))
                .toList();
        if (others.isEmpty()) {
            return;
        }
        canonical.markCollision();
        foodCatalogRepository.save(canonical);
        for (FoodCatalog other : others) {
            other.markCollision();
            foodCatalogRepository.save(other);
        }
    }

    private static String displayName(FoodCatalog food) {
        return food.getNameKo() != null ? food.getNameKo() : food.getName();
    }
}
