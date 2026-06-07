-- 인앱 알림 센터: 읽음/삭제 기능을 위한 상태 컬럼 추가.
-- - is_read: 사용자가 알림을 열람했는지
-- - read_at: 읽은 시각 (UI 통계/분석용, NULL이면 미열람)
-- 기존 알림은 모두 미열람으로 간주.

ALTER TABLE notification_logs
    ADD COLUMN is_read BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN read_at TIMESTAMP   NULL;

-- 미열람 알림을 사용자별로 빠르게 조회 (배지 카운트, 목록 정렬).
CREATE INDEX idx_notification_logs_user_unread
    ON notification_logs(user_id, is_read, sent_at DESC);
