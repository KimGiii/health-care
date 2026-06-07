package com.healthcare.common.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class WeeklyNotificationScheduler {

    private final NotificationService notificationService;

    // 매주 월요일 오전 9시 KST — 지난 한 주 회고 알림
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void sendWeeklySummaries() {
        log.info("[Scheduler] Weekly summary notification triggered");
        try {
            notificationService.sendWeeklySummaryToAll();
        } catch (Exception e) {
            log.error("[Scheduler] Weekly summary failed: {}", e.getMessage(), e);
        }
    }

    // 매일 저녁 18:00 KST — 오늘 어떤 기록도 없는 사용자에게 리마인더.
    // 18시는 첫 운영 기준. 사용자 피드백 쌓이면 user-preferred 시간으로 확장.
    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    public void sendDailyLogReminders() {
        log.info("[Scheduler] Daily log reminder triggered");
        try {
            notificationService.sendDailyLogReminderToAll();
        } catch (Exception e) {
            log.error("[Scheduler] Daily log reminder failed: {}", e.getMessage(), e);
        }
    }

    // 매일 09:00 KST — 아침 식사 미기록자에게 리마인더.
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void sendBreakfastReminders() {
        log.info("[Scheduler] Breakfast reminder triggered");
        try {
            notificationService.sendBreakfastReminderToAll();
        } catch (Exception e) {
            log.error("[Scheduler] Breakfast reminder failed: {}", e.getMessage(), e);
        }
    }

    // 매일 13:00 KST — 점심 식사 미기록자에게 리마인더.
    @Scheduled(cron = "0 0 13 * * *", zone = "Asia/Seoul")
    public void sendLunchReminders() {
        log.info("[Scheduler] Lunch reminder triggered");
        try {
            notificationService.sendLunchReminderToAll();
        } catch (Exception e) {
            log.error("[Scheduler] Lunch reminder failed: {}", e.getMessage(), e);
        }
    }
}
