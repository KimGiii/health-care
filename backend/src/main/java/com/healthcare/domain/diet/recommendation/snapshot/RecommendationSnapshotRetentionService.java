package com.healthcare.domain.diet.recommendation.snapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationSnapshotRetentionService {

    private static final int RETENTION_DAYS = 90;

    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationEventRepository eventRepository;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        List<Long> expiredIds = snapshotRepository.findIdsByCreatedAtBefore(cutoff);
        if (expiredIds.isEmpty()) return;

        eventRepository.deleteAllBySnapshotIdIn(expiredIds);
        snapshotRepository.deleteAllById(expiredIds);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        snapshotRepository.deleteAllByUserId(userId);
    }
}
