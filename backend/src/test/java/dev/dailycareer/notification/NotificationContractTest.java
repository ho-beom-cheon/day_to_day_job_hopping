package dev.dailycareer.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static dev.dailycareer.notification.NotificationViews.ReadAllRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationContractTest {
    @Test void acceptsOnlyCanonicalSortsAndPastReadBoundary() {
        assertThat(NotificationService.order(null)).isEqualTo("created_at desc,id");
        assertThat(NotificationService.order("createdAt,asc")).isEqualTo("created_at,id");
        assertThatCode(() -> NotificationService.validate(new ReadAllRequest(OffsetDateTime.now().minusSeconds(1))))
                .doesNotThrowAnyException();
        assertFailure(() -> NotificationService.order("title,asc"), ApiErrorCode.INVALID_SORT_FIELD);
        assertFailure(() -> NotificationService.validate(new ReadAllRequest(OffsetDateTime.now().plusMinutes(1))), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test void mapsOutboxEventsToSafeContractValues() throws Exception {
        var message = OutboxDispatcher.messageFor("SESSION_COMPLETED", new ObjectMapper().readTree("{}"));
        assertThat(message.type()).isEqualTo("STUDY_REMINDER");
        assertThat(message.targetPath()).startsWith("/").doesNotStartWith("//");
        assertThat(OutboxDispatcher.messageFor("CURRICULUM_ASSIGNED", new ObjectMapper().readTree("{}")).body()).doesNotContain("6개월");
        assertThat(OutboxDispatcher.messageFor("CURRICULUM_COMPLETED", new ObjectMapper().readTree("{}")).title()).doesNotContain("6개월");
        assertThat(OutboxDispatcher.messageFor("UNKNOWN_EVENT", new ObjectMapper().readTree("{}"))).isNull();
    }

    @Test void scheduledReminderUsesKoreanTimeAndDropsLateOrDisabledSlots() throws Exception {
        var enabled = new ObjectMapper().readTree("{\"enabled\":true,\"time\":\"19:30\"}");
        LocalDate date = LocalDate.of(2026, 9, 26);
        assertThat(ScheduledReminderWorker.due(enabled, date, Instant.parse("2026-09-26T10:29:59Z"))).isFalse();
        assertThat(ScheduledReminderWorker.due(enabled, date, Instant.parse("2026-09-26T10:30:00Z"))).isTrue();
        assertThat(ScheduledReminderWorker.due(enabled, date, Instant.parse("2026-09-26T10:34:59Z"))).isTrue();
        assertThat(ScheduledReminderWorker.due(enabled, date, Instant.parse("2026-09-26T10:35:00Z"))).isFalse();
        assertThat(ScheduledReminderWorker.due(new ObjectMapper().readTree("{\"enabled\":false,\"time\":\"19:30\"}"), date,
                Instant.parse("2026-09-26T10:30:00Z"))).isFalse();
    }

    @Test void weeklyReportShowsMeasuredResultsAndHonestEmptyState() throws Exception {
        var json = new ObjectMapper();
        var measured = OutboxDispatcher.messageFor("SCHEDULED_WEEKLY_REPORT", json.readTree("""
                {"report":{"from":"2026-09-14","to":"2026-09-20","totalSessions":9,
                "completedSessions":2,"studyMinutes":85,"firstAttempts":4,"firstCorrect":3}}
                """));
        assertThat(measured.type()).isEqualTo("WEEKLY_REPORT");
        assertThat(measured.targetPath()).isEqualTo("/progress");
        assertThat(measured.body()).contains("2026-09-14 ~ 2026-09-20", "필수 학습 2/9개", "학습 85분", "첫 풀이 정답 3/4개");

        var empty = OutboxDispatcher.messageFor("SCHEDULED_WEEKLY_REPORT", json.readTree("""
                {"report":{"from":"2026-09-14","to":"2026-09-20","totalSessions":0,
                "completedSessions":0,"studyMinutes":0,"firstAttempts":0,"firstCorrect":0}}
                """));
        assertThat(empty.body()).contains("기록된 학습이 없어요").doesNotContain("0/0");
        assertThat(OutboxDispatcher.messageFor("SCHEDULED_WEEKLY_REPORT", json.readTree("{}"))
                .body()).contains("통계에서 확인");
    }

    private static void assertFailure(Runnable action, ApiErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
