package dev.dailycareer.notification;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.time.OffsetDateTime;
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

    @Test void ignoresEventsWithoutAUserFacingMessage() {
        assertThat(OutboxDispatcher.messageFor("SESSION_COMPLETED")).isNull();
        assertThat(OutboxDispatcher.messageFor("UNKNOWN_EVENT")).isNull();
    }

    private static void assertFailure(Runnable action, ApiErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
