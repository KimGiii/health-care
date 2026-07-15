package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.OnlinePreferencePolicy;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1 추천 이벤트 funnel 통합 테스트 — 실 PostgreSQL + 실 Flyway(V40 food_catalog_id) 위에서
 * GENERATED(Store) → REFRESHED(Feedback) → RECORDED(Conversion) 적재와
 * {@code aggregateFoodEngagement} JPQL 집계까지 전 구간을 검증한다.
 *
 * <p>단위 테스트(mock repository)가 증명하지 못하는 것을 증명한다: V40 컬럼 매핑,
 * chk_recommendation_event_reason 제약과 fan-out 행의 공존, 집계 쿼리의 enum 파라미터·
 * 사유 필터·food null 제외가 실제 DB에서 동작하는지. KPI 판정 쿼리(설계 문서)가 읽는
 * 데이터가 실제로 쌓임을 배포 전에 보증한다.
 *
 * <p>{@link com.healthcare.domain.diet.external.importer.FoodCatalogDedupLoadIT}와 동일하게
 * 표준 datasource(POSTGRES_* 환경변수)를 사용한다 — live DB 없이 로컬 실행하면 실패한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@DisplayName("추천 이벤트 funnel IT — 실 PostgreSQL + Flyway")
class RecommendationEventFunnelIT {

    @Autowired private RecommendationSnapshotRepository snapshotRepository;
    @Autowired private RecommendationEventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DietLogRepository dietLogRepository;

    private SnapshotMealsCodec mealsCodec;
    private RecommendationSnapshotStore snapshotStore;
    private RecommendationFeedbackService feedbackService;
    private RecommendationConversionService conversionService;
    private OnlinePreferenceSignalLoader signalLoader;

    private Long userId;

    @BeforeEach
    void setUp() {
        mealsCodec = new SnapshotMealsCodec(new ObjectMapper());
        snapshotStore = new RecommendationSnapshotStore(snapshotRepository, eventRepository, mealsCodec);
        feedbackService = new RecommendationFeedbackService(snapshotRepository, eventRepository, mealsCodec);
        conversionService = new RecommendationConversionService(snapshotRepository, eventRepository, mealsCodec);
        signalLoader = new OnlinePreferenceSignalLoader(eventRepository);

        userId = userRepository.save(User.builder()
                .email("funnel-it@test.local")
                .displayName("funnel-it")
                .build()).getId();
    }

    @Test
    @DisplayName("추천 생성 → 재추천 → 기록 전환이 food별 이벤트로 실제 DB에 쌓이고 집계 쿼리가 신호를 만든다")
    void fullFunnel_persistsFoodLevelEventsAndAggregates() {
        // 1) GENERATED — 스냅샷 저장 시 food별 fan-out (중복 11L은 1회)
        List<RecommendedMeal> meals = List.of(
                meal(MealType.LUNCH, entry(11L), entry(12L)),
                meal(MealType.DINNER, entry(11L), entry(13L)));
        Long snapshotId = snapshotStore.save(
                userId, LocalDate.now(), meals, null, false);

        List<RecommendationEvent> generated = eventRepository.findBySnapshotId(snapshotId);
        assertThat(generated).hasSize(3);
        assertThat(generated).extracting(RecommendationEvent::getFoodCatalogId)
                .containsExactlyInAnyOrder(11L, 12L, 13L);

        // 2) REFRESHED — 음식 기인 사유 fan-out (chk 제약: 모든 행에 reason 보존)
        feedbackService.recordFeedback(userId, snapshotId, RecommendationFeedbackReason.NUTRITION_DISLIKE);
        // 산입 제외 사유도 저장은 된다(저장은 사실, 해석은 정책)
        feedbackService.recordFeedback(userId, snapshotId, RecommendationFeedbackReason.NOT_HUNGRY);

        // 3) RECORDED — 기록 식품 ∩ 스냅샷 식품 교집합만 귀속
        DietLog dietLog = dietLogRepository.save(DietLog.builder()
                .userId(userId)
                .logDate(LocalDate.now())
                .mealType(MealType.LUNCH)
                .build());
        conversionService.recordConversion(userId, snapshotId, dietLog, List.of(11L, 999L));

        List<RecommendationEvent> all = eventRepository.findBySnapshotId(snapshotId);
        assertThat(all.stream().filter(e -> e.getEventType() == EventType.REFRESHED)).hasSize(6);
        List<RecommendationEvent> recorded = all.stream()
                .filter(e -> e.getEventType() == EventType.RECORDED).toList();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().getFoodCatalogId()).isEqualTo(11L);
        assertThat(recorded.getFirst().getDietLogId()).isEqualTo(dietLog.getId());
        assertThat(dietLogRepository.findById(dietLog.getId()).orElseThrow()
                .getRecommendationSnapshotId()).isEqualTo(snapshotId);

        // 4) 집계 — RECORDED 우대, REFRESHED는 음식 기인 사유만 산입(NOT_HUNGRY 제외)
        OnlinePreferencePolicy policy = signalLoader.load(userId);
        assertThat(policy.penaltyScore(11L)).isEqualTo(0.0);  // RECORDED 1 - REFRESHED(NUTRITION_DISLIKE) 1
        assertThat(policy.penaltyScore(12L)).isEqualTo(1.0);  // REFRESHED(NUTRITION_DISLIKE) 1만 — NOT_HUNGRY 미산입
        assertThat(policy.penaltyScore(999L)).isZero();
    }

    @Test
    @DisplayName("만료된 스냅샷으로 기록해도 기록은 보호되고 이벤트만 스킵된다 (best-effort)")
    void conversion_missingSnapshot_keepsDietLogSafe() {
        DietLog dietLog = dietLogRepository.save(DietLog.builder()
                .userId(userId)
                .logDate(LocalDate.now())
                .mealType(MealType.DINNER)
                .build());

        conversionService.recordConversion(userId, 999_999L, dietLog, List.of(11L));

        assertThat(dietLogRepository.findById(dietLog.getId())).isPresent();
        assertThat(dietLogRepository.findById(dietLog.getId()).orElseThrow()
                .getRecommendationSnapshotId()).isNull();
        assertThat(eventRepository.count()).isZero();
    }

    // ─── 헬퍼 ───

    private RecommendedMeal meal(MealType mealType, RecommendedFoodEntry... items) {
        return new RecommendedMeal(mealType, 500, 450, 30, 40, 12, List.of(items));
    }

    private RecommendedFoodEntry entry(Long foodCatalogId) {
        return new RecommendedFoodEntry(
                foodCatalogId, "food-" + foodCatalogId, "식품-" + foodCatalogId,
                null, 100, 150, 10, 15, 4, null, null);
    }
}
