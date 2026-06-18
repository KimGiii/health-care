package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.diet.dto.CreateDietLogRequest;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType;
import com.healthcare.domain.diet.repository.DietLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 → 기록 전환 (RECORDED 이벤트) 단위 테스트")
class RecommendationConversionTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    @Mock
    private RecommendationEventRepository eventRepository;

    @InjectMocks
    private RecommendationConversionService conversionService;

    @Test
    @DisplayName("유효한 snapshotId로 기록 전환 시 RECORDED 이벤트가 저장된다")
    void recordConversion_validSnapshot_savesRecordedEvent() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.now(), "[]", null, false);
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshot));

        conversionService.recordConversion(1L, 10L, 99L);

        ArgumentCaptor<RecommendationEvent> captor =
                ArgumentCaptor.forClass(RecommendationEvent.class);
        verify(eventRepository).save(captor.capture());

        RecommendationEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.RECORDED);
        assertThat(event.getDietLogId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("존재하지 않는 snapshotId면 ResourceNotFoundException을 던진다")
    void recordConversion_unknownSnapshot_throws() {
        given(snapshotRepository.findByIdAndUserId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.recordConversion(1L, 999L, 42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
