package com.healthcare.domain.diet.recommendation.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationSnapshot 도메인 모델")
class RecommendationSnapshotTest {

    @Test
    @DisplayName("스냅샷 생성 시 GENERATED 이벤트가 자동으로 추가된다")
    void newSnapshotHasGeneratedEvent() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.of(2026, 6, 19), "[]", null, false);

        assertThat(snapshot.getEvents()).hasSize(1);
        assertThat(snapshot.getEvents().getFirst().getEventType())
                .isEqualTo(RecommendationEvent.EventType.GENERATED);
    }

    @Test
    @DisplayName("recordExposed() 호출 시 EXPOSED 이벤트가 추가된다")
    void recordExposedAddsEvent() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.now(), "[]", null, false);

        snapshot.recordExposed();

        assertThat(snapshot.getEvents()).hasSize(2);
        assertThat(snapshot.getEvents().getLast().getEventType())
                .isEqualTo(RecommendationEvent.EventType.EXPOSED);
    }

    @Test
    @DisplayName("recordRefreshed()는 feedback reason과 함께 REFRESHED 이벤트를 추가한다")
    void recordRefreshedAddsEventWithReason() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.now(), "[]", null, false);

        snapshot.recordRefreshed(RecommendationFeedbackReason.NOT_HUNGRY);

        RecommendationEvent event = snapshot.getEvents().getLast();
        assertThat(event.getEventType()).isEqualTo(RecommendationEvent.EventType.REFRESHED);
        assertThat(event.getFeedbackReason()).isEqualTo(RecommendationFeedbackReason.NOT_HUNGRY);
    }

    @Test
    @DisplayName("recordConverted()는 dietLogId와 함께 RECORDED 이벤트를 추가한다")
    void recordConvertedAddsEventWithDietLogId() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.now(), "[]", null, false);

        snapshot.recordConverted(42L);

        RecommendationEvent event = snapshot.getEvents().getLast();
        assertThat(event.getEventType()).isEqualTo(RecommendationEvent.EventType.RECORDED);
        assertThat(event.getDietLogId()).isEqualTo(42L);
    }
}
