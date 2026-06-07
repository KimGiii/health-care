package com.healthcare.common.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private Instant readAt;

    @Builder
    public NotificationLog(Long userId, String type, String title, String body,
                           String status, String fcmToken, String errorMessage) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.status = status;
        this.fcmToken = fcmToken;
        this.errorMessage = errorMessage;
        this.sentAt = Instant.now();
        this.isRead = false;
    }

    /** 사용자가 알림 열람 처리. 이미 읽은 상태면 no-op (read_at 보존). */
    public void markRead() {
        if (this.isRead) return;
        this.isRead = true;
        this.readAt = Instant.now();
    }
}
