package com.healthcare.common.notification;

import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.insights.dto.WeeklySummaryResponse;
import com.healthcare.domain.insights.service.InsightsService;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private FcmService fcmService;
    @Mock private NotificationLogRepository logRepository;
    @Mock private UserRepository userRepository;
    @Mock private InsightsService insightsService;
    @Mock private DietLogRepository dietLogRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(fcmService, logRepository, userRepository, insightsService, dietLogRepository);
    }

    private User makeUser(Long id, String fcmToken) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getFcmToken()).thenReturn(fcmToken);
        return user;
    }

    private WeeklySummaryResponse makeSummary(int exerciseCount, int exerciseMinutes, Double avgCal) {
        return WeeklySummaryResponse.builder()
                .weekStart(LocalDate.now().minusDays(6))
                .weekEnd(LocalDate.now())
                .weekOffset(0)
                .exerciseSessionCount(exerciseCount)
                .totalExerciseMinutes(exerciseMinutes)
                .avgDailyCalories(avgCal)
                .build();
    }

    @Test
    @DisplayName("FCM 토큰 있는 사용자에게 주간 알림 발송")
    void sendWeeklySummaryToAll_usersWithToken_sendNotification() {
        User user = makeUser(1L, "token-abc");
        when(userRepository.findAllWithFcmToken()).thenReturn(List.of(user));
        when(logRepository.existsByUserIdAndTypeAndSentAtAfter(anyLong(), anyString(), any())).thenReturn(false);
        when(insightsService.getWeeklySummary(1L, 0)).thenReturn(makeSummary(3, 90, 1800.0));
        when(fcmService.send(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmService.FcmResult.mocked());

        service.sendWeeklySummaryToAll();

        verify(fcmService).send(eq("token-abc"), anyString(), anyString(), anyMap());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("이미 이번 주 발송한 사용자는 중복 발송 안 함")
    void sendWeeklySummaryToAll_alreadySentThisWeek_skipUser() {
        User user = makeUser(2L, "token-xyz");
        when(userRepository.findAllWithFcmToken()).thenReturn(List.of(user));
        when(logRepository.existsByUserIdAndTypeAndSentAtAfter(anyLong(), anyString(), any(Instant.class)))
                .thenReturn(true);

        service.sendWeeklySummaryToAll();

        verifyNoInteractions(fcmService);
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("FCM 토큰 없는 사용자 목록이 비어 있으면 발송 없음")
    void sendWeeklySummaryToAll_noUsersWithToken_nothingSent() {
        when(userRepository.findAllWithFcmToken()).thenReturn(List.of());

        service.sendWeeklySummaryToAll();

        verifyNoInteractions(fcmService);
    }

    @Test
    @DisplayName("FCM 발송 실패 시 FAILED 상태로 로그 기록")
    void sendWeeklySummaryToAll_fcmFails_logsFailedStatus() {
        User user = makeUser(3L, "bad-token");
        when(userRepository.findAllWithFcmToken()).thenReturn(List.of(user));
        when(logRepository.existsByUserIdAndTypeAndSentAtAfter(anyLong(), anyString(), any())).thenReturn(false);
        when(insightsService.getWeeklySummary(3L, 0)).thenReturn(makeSummary(0, 0, null));
        when(fcmService.send(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmService.FcmResult.failed("invalid token"));

        service.sendWeeklySummaryToAll();

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("invalid token");
    }
}
