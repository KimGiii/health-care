package com.healthcare.common.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * 인앱 알림 센터 조회/상태 변경 서비스.
 *
 * 발송 책임(NotificationService)과 분리:
 * - NotificationService: 외부 FCM 발송 + NotificationLog 작성
 * - NotificationCenterService: 사용자가 본 알림 목록 조회/읽음/삭제
 */
@Service
@RequiredArgsConstructor
public class NotificationCenterService {

    private final NotificationLogRepository repository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderBySentAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        NotificationLog log = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다"));
        log.markRead();
        return NotificationResponse.from(log);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllReadForUser(userId, Instant.now());
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        NotificationLog log = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다"));
        repository.delete(log);
    }
}
