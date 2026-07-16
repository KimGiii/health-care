package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.goals.entity.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스냅샷은 순수 저장 모델이다 — 이벤트 생성·영속화는 snapshot 패키지 서비스들
 * (Store/Feedback/Conversion)의 책임이며 각 서비스 테스트가 검증한다.
 * (과거 @Transient 이벤트 리스트는 GENERATED 유실 버그의 원인이라 제거됨)
 */
@DisplayName("RecommendationSnapshot 도메인 모델")
class RecommendationSnapshotTest {

    @Test
    @DisplayName("create()는 스냅샷 필드를 채우고 생성 시각을 기록한다")
    void createPopulatesFields() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.of(2026, 7, 15), "[]", Goal.GoalType.WEIGHT_LOSS, true);

        assertThat(snapshot.getUserId()).isEqualTo(1L);
        assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(snapshot.getMealsJson()).isEqualTo("[]");
        assertThat(snapshot.getGoalType()).isEqualTo(Goal.GoalType.WEIGHT_LOSS);
        assertThat(snapshot.isStrictAllergyMode()).isTrue();
        assertThat(snapshot.getCreatedAt()).isNotNull();
    }
}
