package dev.dailycareer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import dev.dailycareer.notification.push.WebPushSettings;
import dev.dailycareer.integration.slack.SlackSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.dailycareer.integration.IntegrationViews.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationContractTest {
    @Test void testReminderUsesCanonicalJsonFieldAndRange() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(new TestReminder(true, 60));
        assertThat(payload).contains("\"beforeMinutes\":60").doesNotContain("\"minutesBefore\"");

        assertThatCode(() -> IntegrationService.validate(patch(1))).doesNotThrowAnyException();
        assertThatCode(() -> IntegrationService.validate(patch(1440))).doesNotThrowAnyException();
        assertValidationFailure(0);
        assertValidationFailure(1441);
    }

    @Test void enablingUnconfiguredSlackReturnsCanonicalConflict() {
        IntegrationService service = service(mock(JdbcTemplate.class), "", "");
        assertThatThrownBy(() -> service.updateSlack(1L, new SlackPatch(true, List.of("STUDY_REMINDER")), 0))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.SLACK_NOT_CONFIGURED);
                    assertThat(exception.code().status()).isEqualTo(409);
                });
    }

    @Test void storesConfiguredSlackCredentialReferenceInsteadOfPlaceholder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        when(jdbc.update(anyString(), eq(1L), eq("vault://slack/personal"), anyString())).thenReturn(1);

        service(jdbc, "vault://slack/personal", "https://hooks.slack.com/services/test/value")
                .updateSlack(1L, new SlackPatch(true, List.of("STUDY_REMINDER")), 0);

        verify(jdbc).update(anyString(), eq(1L), eq("vault://slack/personal"), anyString());
    }

    private static NotificationSettingsPatch patch(int beforeMinutes) {
        return new NotificationSettingsPatch(null, new TestReminder(true, beforeMinutes), null, null, null, null);
    }

    private static void assertValidationFailure(int beforeMinutes) {
        assertThatThrownBy(() -> IntegrationService.validate(patch(beforeMinutes)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    private static IntegrationService service(JdbcTemplate jdbc, String credentialRef, String webhookUrl) {
        return new IntegrationService(jdbc, mock(UserAccountService.class), new ObjectMapper(),
                new WebPushSettings("", "", "", "", "", ""),
                new SlackSettings(credentialRef, webhookUrl, "#daily-career", "hooks.slack.com"));
    }
}
