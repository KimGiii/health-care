package com.healthcare.common.notification;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 인앱 알림 센터 API.
 * - 목록: 최신 발송 순 (페이징)
 * - 미열람 개수: 배지 표시
 * - 읽음 처리: 단건/전체
 * - 삭제: 단건
 *
 * 권한: @CurrentUserId 기반. 본인 소유 알림만 접근 가능.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationCenterService notificationCenterService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<NotificationResponse> result = notificationCenterService.list(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@CurrentUserId Long userId) {
        long count = notificationCenterService.unreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @CurrentUserId Long userId,
            @PathVariable Long id
    ) {
        NotificationResponse updated = notificationCenterService.markRead(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(@CurrentUserId Long userId) {
        int updated = notificationCenterService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUserId Long userId,
            @PathVariable Long id
    ) {
        notificationCenterService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("삭제되었습니다", null));
    }
}
