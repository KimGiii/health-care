package com.healthcare.common.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByUserIdAndTypeAndSentAtAfter(Long userId, String type, Instant after);

    /** 사용자별 알림 목록 (최신 발송 순). 페이징은 호출자가 제어. */
    Page<NotificationLog> findByUserIdOrderBySentAtDesc(Long userId, Pageable pageable);

    /** 사용자별 미열람 알림 개수 — 배지 표시용. */
    long countByUserIdAndIsReadFalse(Long userId);

    /** 본인 소유 알림만 조회 (권한 체크 겸용). */
    Optional<NotificationLog> findByIdAndUserId(Long id, Long userId);

    /**
     * 사용자의 모든 미열람 알림을 일괄 읽음 처리.
     * @return 업데이트된 row 수
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationLog n SET n.isRead = true, n.readAt = :now " +
           "WHERE n.userId = :userId AND n.isRead = false")
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
