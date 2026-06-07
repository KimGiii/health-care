package com.healthcare.common.notification;

import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.insights.service.InsightsService;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FcmService fcmService;
    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;
    private final InsightsService insightsService;
    private final DietLogRepository dietLogRepository;

    /**
     * 전체 사용자에게 주간 요약 발송.
     *
     * <p>H-4 대응: 단일 대형 {@code @Transactional} 안에서 N명의 외부 HTTP(FCM) 호출이
     * 일어나면 한 건의 FCM 타임아웃이 전체 트랜잭션을 막거나 롤백시킨다. 따라서:
     * <ul>
     *   <li>외부 루프는 트랜잭션 없이 실행한다.</li>
     *   <li>FCM 전송은 트랜잭션 밖에서 수행한다.</li>
     *   <li>{@code NotificationLog} 저장만 짧은 트랜잭션(JPA 기본 동작)으로 처리한다.</li>
     *   <li>한 사용자 처리 중 예외가 발생해도 다음 사용자는 계속 진행한다.</li>
     * </ul>
     */
    public void sendWeeklySummaryToAll() {
        Instant cutoff = Instant.now().minus(6, ChronoUnit.DAYS);
        int sent = 0, skipped = 0, failed = 0;

        for (User user : userRepository.findAllWithFcmToken()) {
            if (notificationLogRepository.existsByUserIdAndTypeAndSentAtAfter(
                    user.getId(), NotificationType.WEEKLY_SUMMARY, cutoff)) {
                skipped++;
                continue;
            }

            try {
                sendWeeklySummary(user);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("[Notification] Weekly summary failed userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }

        log.info("[Notification] Weekly summary — sent={} skipped={} failed={}", sent, skipped, failed);
    }

    private void sendWeeklySummary(User user) {
        var summary = insightsService.getWeeklySummary(user.getId(), 0);

        String title = "이번 주 건강 요약";
        String body = buildWeeklySummaryBody(summary.getExerciseSessionCount(),
                summary.getTotalExerciseMinutes(), summary.getAvgDailyCalories());

        // FCM 호출은 트랜잭션 밖에서 — 외부 HTTP 호출이 DB 트랜잭션을 점유하지 않도록.
        FcmService.FcmResult result = fcmService.send(
                user.getFcmToken(), title, body,
                Map.of("type", NotificationType.WEEKLY_SUMMARY,
                        "weekStart", summary.getWeekStart().toString()));

        // Spring Data JPA의 save()는 자체 트랜잭션을 시작 — 외부 트랜잭션이 필요 없다.
        NotificationLog logEntry = NotificationLog.builder()
                .userId(user.getId())
                .type(NotificationType.WEEKLY_SUMMARY)
                .title(title)
                .body(body)
                .status(result.isSent() ? "SENT" : "FAILED")
                .fcmToken(user.getFcmToken())
                .errorMessage(result.isSent() ? null : result.detail())
                .build();
        notificationLogRepository.save(logEntry);
    }

    private String buildWeeklySummaryBody(int exerciseCount, int exerciseMinutes,
                                           Double avgCalories) {
        StringBuilder sb = new StringBuilder();

        if (exerciseCount > 0) {
            sb.append("운동 ").append(exerciseCount).append("회 (")
              .append(exerciseMinutes).append("분)");
        } else {
            sb.append("이번 주 운동 기록이 없어요");
        }

        if (avgCalories != null && avgCalories > 0) {
            sb.append(" · 평균 ").append(Math.round(avgCalories)).append("kcal");
        }

        return sb.toString();
    }

    // MARK: - Daily Log Reminder
    //
    // 매일 저녁 운영 cutoff 시각(기본 18:00 KST) 이후, 오늘 어떤 기록도 없는
    // 사용자에게 "오늘의 기록을 추가해 보세요" 알림을 발송.

    /**
     * 오늘 미기록 사용자 전체에게 일일 리마인더 발송.
     * 같은 일자에 이미 한 번 발송된 사용자는 skip (중복 방지).
     */
    public void sendDailyLogReminderToAll() {
        LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Instant startOfTodayKst = todayKst.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        int sent = 0, skipped = 0, failed = 0;

        for (User user : userRepository.findAllWithFcmToken()) {
            // 오늘 이미 발송했으면 skip
            if (notificationLogRepository.existsByUserIdAndTypeAndSentAtAfter(
                    user.getId(), NotificationType.DAILY_LOG_REMINDER, startOfTodayKst)) {
                skipped++;
                continue;
            }
            // 오늘 기록이 하나라도 있으면 skip
            if (insightsService.hasAnyActivityOn(user.getId(), todayKst)) {
                skipped++;
                continue;
            }

            try {
                sendDailyLogReminder(user);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("[Notification] Daily log reminder failed userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }

        log.info("[Notification] Daily log reminder — sent={} skipped={} failed={}", sent, skipped, failed);
    }

    private void sendDailyLogReminder(User user) {
        String title = "오늘 기록을 잊지 마세요";
        String body  = "운동·식단 중 하나라도 가볍게 남겨 두면 추세를 놓치지 않아요";

        FcmService.FcmResult result = fcmService.send(
                user.getFcmToken(), title, body,
                Map.of("type", NotificationType.DAILY_LOG_REMINDER));

        NotificationLog logEntry = NotificationLog.builder()
                .userId(user.getId())
                .type(NotificationType.DAILY_LOG_REMINDER)
                .title(title)
                .body(body)
                .status(result.isSent() ? "SENT" : "FAILED")
                .fcmToken(user.getFcmToken())
                .errorMessage(result.isSent() ? null : result.detail())
                .build();
        notificationLogRepository.save(logEntry);
    }

    // MARK: - Meal Reminders
    //
    // 식사 시간대 cutoff 시각이 되면 해당 끼니 미기록 사용자에게 리마인더 발송.
    // - BREAKFAST: 09:00 KST
    // - LUNCH:     13:00 KST
    // 저녁은 18:00 DAILY_LOG_REMINDER가 어떤 기록이라도 없는 사용자 대상이라 별도 메시지 생략.

    public void sendBreakfastReminderToAll() {
        sendMealReminderToAll(
                DietLog.MealType.BREAKFAST,
                NotificationType.MEAL_BREAKFAST_REMINDER,
                "아침 식사를 기록해 주세요",
                "오늘 아침 한 끼만 가볍게 남겨도 추세 그래프가 살아나요"
        );
    }

    public void sendLunchReminderToAll() {
        sendMealReminderToAll(
                DietLog.MealType.LUNCH,
                NotificationType.MEAL_LUNCH_REMINDER,
                "점심 식사를 기록해 주세요",
                "점심 칼로리·매크로를 가볍게 남기면 하루 균형이 보여요"
        );
    }

    /**
     * 특정 mealType 미기록 사용자에게 일괄 리마인더 발송.
     * 같은 일자 중복 발송 방지 + 이미 해당 끼니 기록이 있으면 skip.
     */
    private void sendMealReminderToAll(DietLog.MealType mealType, String type,
                                        String title, String body) {
        LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Instant startOfTodayKst = todayKst.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        int sent = 0, skipped = 0, failed = 0;

        for (User user : userRepository.findAllWithFcmToken()) {
            if (notificationLogRepository.existsByUserIdAndTypeAndSentAtAfter(
                    user.getId(), type, startOfTodayKst)) {
                skipped++;
                continue;
            }
            if (dietLogRepository.existsByUserIdAndLogDateAndMealType(
                    user.getId(), todayKst, mealType)) {
                skipped++;
                continue;
            }

            try {
                sendMealReminder(user, type, title, body);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("[Notification] Meal reminder failed userId={} type={}: {}",
                        user.getId(), type, e.getMessage());
            }
        }

        log.info("[Notification] Meal reminder({}) — sent={} skipped={} failed={}",
                type, sent, skipped, failed);
    }

    private void sendMealReminder(User user, String type, String title, String body) {
        FcmService.FcmResult result = fcmService.send(
                user.getFcmToken(), title, body,
                Map.of("type", type));

        NotificationLog logEntry = NotificationLog.builder()
                .userId(user.getId())
                .type(type)
                .title(title)
                .body(body)
                .status(result.isSent() ? "SENT" : "FAILED")
                .fcmToken(user.getFcmToken())
                .errorMessage(result.isSent() ? null : result.detail())
                .build();
        notificationLogRepository.save(logEntry);
    }

    public interface NotificationType {
        String WEEKLY_SUMMARY          = "WEEKLY_SUMMARY";
        String DAILY_LOG_REMINDER      = "DAILY_LOG_REMINDER";
        String MEAL_BREAKFAST_REMINDER = "MEAL_BREAKFAST_REMINDER";
        String MEAL_LUNCH_REMINDER     = "MEAL_LUNCH_REMINDER";
    }
}
