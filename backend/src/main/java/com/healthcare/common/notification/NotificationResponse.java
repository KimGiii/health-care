package com.healthcare.common.notification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * iOS 알림 센터에 표시되는 알림 항목 DTO.
 * NotificationLog 엔티티에서 클라이언트에 노출할 필드만 추린다.
 * fcm_token / error_message 같은 내부 진단 필드는 제외.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Instant sentAt,
        boolean read,
        Instant readAt
) {
    public static NotificationResponse from(NotificationLog log) {
        return new NotificationResponse(
                log.getId(),
                log.getType(),
                log.getTitle(),
                log.getBody(),
                log.getSentAt(),
                log.isRead(),
                log.getReadAt()
        );
    }
}
