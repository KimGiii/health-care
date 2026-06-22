package com.healthcare.domain.diet.recommendation.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationSnapshotRetentionService 단위 테스트")
class RecommendationSnapshotRetentionServiceTest {

    @Mock private RecommendationSnapshotRepository snapshotRepository;
    @Mock private RecommendationEventRepository eventRepository;

    @InjectMocks
    private RecommendationSnapshotRetentionService retentionService;

    @Test
    @DisplayName("deleteExpired()는 90일 초과 스냅샷의 이벤트 먼저 삭제 후 스냅샷을 삭제한다")
    void deleteExpired_deletesEventsBeforeSnapshots() {
        given(snapshotRepository.findIdsByCreatedAtBefore(any()))
                .willReturn(List.of(1L, 2L, 3L));

        retentionService.deleteExpired();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(snapshotRepository).findIdsByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(OffsetDateTime.now().minusDays(89));

        verify(eventRepository).deleteAllBySnapshotIdIn(List.of(1L, 2L, 3L));
        verify(snapshotRepository).deleteAllById(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("만료 스냅샷이 없으면 삭제 쿼리를 실행하지 않는다")
    void deleteExpired_noExpiredSnapshots_skipsDelete() {
        given(snapshotRepository.findIdsByCreatedAtBefore(any())).willReturn(List.of());

        retentionService.deleteExpired();

        verify(eventRepository, never()).deleteAllBySnapshotIdIn(any());
        verify(snapshotRepository, never()).deleteAllById(any());
    }

    @Test
    @DisplayName("deleteByUserId()는 해당 사용자 스냅샷 전체를 cascade 삭제한다")
    void deleteByUserId_deletesAllUserSnapshots() {
        retentionService.deleteByUserId(42L);

        verify(snapshotRepository).deleteAllByUserId(42L);
    }
}
