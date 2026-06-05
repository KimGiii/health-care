-- notification_logs: (user_id, type, sent_at) 복합 인덱스
-- NotificationService.existsByUserIdAndTypeAndSentAtAfter() 쿼리 최적화
CREATE INDEX idx_notification_logs_user_type_sent
    ON notification_logs (user_id, type, sent_at DESC);

-- food_entries: food_catalog_id 인덱스
-- FoodCatalog FK 조회 최적화
CREATE INDEX idx_food_entries_food_catalog_id
    ON food_entries (food_catalog_id);
