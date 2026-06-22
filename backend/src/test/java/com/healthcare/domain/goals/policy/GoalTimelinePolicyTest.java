package com.healthcare.domain.goals.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("목표 기간 정책")
class GoalTimelinePolicyTest {

    private final GoalTimelinePolicy policy = new GoalTimelinePolicy();

    @Test
    @DisplayName("희망 목표일이 너무 짧아도 보존하고 안전 주간 변화율로 예상일을 제안한다")
    void preservesRequestedDateAndProjectsAchievableDate() {
        GoalTimelineProjection projection = policy.evaluate(new GoalTimelineInput(
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 7, 1),
                5.0,
                0.5,
                null
        ));

        assertThat(projection.requestedTargetDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(projection.projectedTargetDate()).isEqualTo(LocalDate.of(2026, 8, 27));
    }

    @Test
    @DisplayName("목표 재계산은 같은 ISO 주에는 반복하지 않고 다음 주에 다시 허용한다")
    void recalculatesAtMostOncePerIsoWeek() {
        GoalTimelineProjection sameWeek = policy.evaluate(new GoalTimelineInput(
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 9, 1),
                5.0,
                0.5,
                LocalDate.of(2026, 6, 15)
        ));
        GoalTimelineProjection nextWeek = policy.evaluate(new GoalTimelineInput(
                LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 9, 1),
                5.0,
                0.5,
                LocalDate.of(2026, 6, 15)
        ));

        assertThat(sameWeek.recalculationDue()).isFalse();
        assertThat(nextWeek.recalculationDue()).isTrue();
    }
}
