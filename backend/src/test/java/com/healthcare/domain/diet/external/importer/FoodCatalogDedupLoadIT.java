package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.dedup.CanonicalDedupResolver;
import com.healthcare.domain.diet.external.dedup.DedupCollisionQueueResponse;
import com.healthcare.domain.diet.external.dedup.DedupCollisionQueueService;
import com.healthcare.domain.diet.external.dedup.FoodCatalogCanonicalBackfillService;
import com.healthcare.domain.diet.external.dedup.FoodCatalogCanonicalBackfillSummary;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dedup 적재 정합성 통합 테스트(G1 게이트). 실제 PostgreSQL + 실제 Flyway 마이그레이션
 * (V39 부분 유니크 인덱스 포함) 위에서 적재 경로 전체({@link FoodCatalogIngestService} →
 * {@link CanonicalDedupResolver} → ServingOption 게이트 → 검색·충돌 큐)를 검증한다.
 *
 * <p>표준 datasource(application 설정 + {@code POSTGRES_*} 환경변수)를 사용한다 — 이 프로젝트의 통합 테스트는
 * CI의 postgres 서비스 컨테이너(ci-backend.yml) 위에서 돈다. 로컬 실행 시에도 빈 PostgreSQL을 가리키면 된다.
 * (prod RDS는 17.x. 적재에 쓰는 부분 유니크 인덱스·{@code FUNCTION('replace')}는 16/17 동작 동일.)
 *
 * <p>단위 테스트(stateful fake)가 증명하지 못하는 것을 증명한다: 실제 부분 유니크 인덱스
 * {@code uq_food_catalog_canonical} 가 위반 없이 서는지, 실제 repository 쿼리가 패자를 제외하는지,
 * ServingOption 이 대표 행에만 생성되는지, 강등된 구 대표의 옵션이 백필로 정리되는지.
 * census project 가 산출한 acceptance target(canonical/superseded/COLLISION 구분)의 축소판 시나리오다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({
        FoodCatalogIngestService.class,
        CanonicalDedupResolver.class,
        ServingOptionDeriver.class,
        DedupCollisionQueueService.class,
        FoodCatalogCanonicalBackfillService.class
})
@DisplayName("dedup 적재 정합성 IT — 실 PostgreSQL + Flyway")
class FoodCatalogDedupLoadIT {

    private static final int OPTIONS_PER_CANONICAL = 4; // ServingOptionDeriver MULTIPLIERS 길이

    @Autowired private FoodCatalogIngestService ingestService;
    @Autowired private FoodCatalogCanonicalBackfillService backfillService;
    @Autowired private DedupCollisionQueueService collisionQueueService;
    @Autowired private FoodCatalogRepository catalogRepository;
    @Autowired private FoodServingOptionRepository servingOptionRepository;

    @BeforeEach
    void cleanSlate() {
        // 전역 count 단언이 사전 데이터에 흔들리지 않도록 트랜잭션 내에서 초기화(테스트 종료 시 롤백).
        servingOptionRepository.deleteAllInBatch();
        catalogRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("출처 우선순위 수렴 + 게이트 — 대표만 노출·옵션 보유, 패자 숨김, 충돌 분리")
    void convergesAndGatesExcludeLosers() {
        // 클러스터별 최고 우선순위를 먼저 적재해 강등 없이 정상 상태를 만든다.
        ingestService.ingest(List.of(
                accepted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "D100", "김치찌개"),   // 대표(300)
                accepted(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D100", "김치찌개"),     // 패자(200)
                accepted(FoodCatalogSource.MFDS_STANDARD_DISH, "D100", "김치찌개"),        // 패자(100)
                accepted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P200", "라면"),       // 자체중복 1
                accepted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P200", "라면"),       // 자체중복 2 → upsert
                accepted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X300", "사과"),       // 충돌 A
                accepted(FoodCatalogSource.MFDS_STANDARD_DISH, "X300", "배"),             // 충돌 B(같은 코드·다른 이름)
                accepted(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "N400", "두부")          // 독립 대표
        ), FoodCatalogIngestCurationMode.PRESERVE_EXISTING);

        // dedup 상태 분포: CANONICAL 3(D100 가공·P200·N400), SUPERSEDED 2(D100 영양DB·음식), COLLISION 2(X300 사과·배)
        assertThat(catalogRepository.countByIsCustomFalseAndDedupState(DedupState.CANONICAL)).isEqualTo(3);
        assertThat(catalogRepository.countByIsCustomFalseAndDedupState(DedupState.SUPERSEDED)).isEqualTo(2);
        assertThat(catalogRepository.countByIsCustomFalseAndDedupState(DedupState.COLLISION)).isEqualTo(2);

        // 대표(canonical_group_id IS NULL) = CANONICAL 3 + COLLISION 2 = 5, 각 4개 옵션 → 20. 패자는 0.
        assertThat(servingOptionRepository.count()).isEqualTo(5L * OPTIONS_PER_CANONICAL);
        for (FoodCatalog loser : catalogRepository.findByDedupStateAndIsCustomFalse(DedupState.SUPERSEDED)) {
            assertThat(servingOptionRepository.existsByFoodCatalogId(loser.getId()))
                    .as("패자 행 %s 는 옵션이 없어야 한다", loser.getFoodCode())
                    .isFalse();
        }

        // 검색 게이트: 같은 코드·이름의 3행 중 대표(가공)만 노출.
        List<FoodCatalog> hits = catalogRepository.searchAll("김치찌개", null, false);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getSource()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED);

        // 충돌 검토 큐: X300 한 코드, 멤버 2(사과·배).
        DedupCollisionQueueResponse queue = collisionQueueService.queue(null, 50);
        assertThat(queue.groupCount()).isEqualTo(1);
        assertThat(queue.groups().get(0).foodCode()).isEqualTo("X300");
        assertThat(queue.groups().get(0).members()).hasSize(2);
    }

    @Test
    @DisplayName("강등 구 대표의 잔여 옵션을 백필이 정리한다(옵션 폭증 차단)")
    void backfillClearsDemotedRepresentativeOptions() {
        // 낮은 우선순위부터 적재 → 적재 중 강등 발생. 강등된 구 대표의 옵션은 인라인 정리되지 않는다(설계상 백필 보완).
        ingestService.ingest(List.of(
                accepted(FoodCatalogSource.MFDS_STANDARD_DISH, "D500", "제육볶음"),       // 대표였다가 강등
                accepted(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "D500", "제육볶음"),    // 대표였다가 강등
                accepted(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "D500", "제육볶음")   // 최종 대표(300)
        ), FoodCatalogIngestCurationMode.PRESERVE_EXISTING);

        // 강등 전 각 행이 한 번씩 대표였으므로 옵션이 3행×4 = 12개 남아 있다(구 대표 옵션 미정리 상태).
        assertThat(servingOptionRepository.count()).isEqualTo(3L * OPTIONS_PER_CANONICAL);
        assertThat(catalogRepository.countByIsCustomFalseAndDedupState(DedupState.SUPERSEDED)).isEqualTo(2);

        FoodCatalogCanonicalBackfillSummary summary = backfillService.backfill();

        // 백필 후: 대표 1행만 옵션 보유(4), 패자 2행 옵션 정리됨.
        assertThat(summary.servingOptionsCleared()).isEqualTo(2 * OPTIONS_PER_CANONICAL);
        assertThat(servingOptionRepository.count()).isEqualTo(OPTIONS_PER_CANONICAL);
        for (FoodCatalog loser : catalogRepository.findByDedupStateAndIsCustomFalse(DedupState.SUPERSEDED)) {
            assertThat(servingOptionRepository.existsByFoodCatalogId(loser.getId())).isFalse();
        }
    }

    private FoodCatalogIngestCandidate accepted(FoodCatalogSource source, String code, String nameKo) {
        return FoodCatalogIngestCandidate.accepted(FoodCatalog.builder()
                .source(source)
                .foodCode(code)
                .name(nameKo)
                .nameKo(nameKo)
                .category(FoodCategory.OTHER)
                .caloriesPer100g(120.0)
                .isCustom(false)
                .build());
    }
}
