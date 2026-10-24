package dev.dailycareer.user;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static dev.dailycareer.user.SettingsViews.SettingsPatch;
import static dev.dailycareer.user.SettingsViews.StudyPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingsContractTest {
    @Test void acceptsCanonicalBoundaryValues() {
        assertThatCode(() -> UserSettingsService.validate(new SettingsPatch(
                new StudyPolicy(15, 0, List.of()), "00:00", "23:59", "LIGHT")))
                .doesNotThrowAnyException();
        assertThatCode(() -> UserSettingsService.validate(new SettingsPatch(
                new StudyPolicy(480, 2, List.of("SATURDAY", "SUNDAY")), null, null, "SYSTEM")))
                .doesNotThrowAnyException();
    }

    @Test void rejectsInvalidPolicyTimeAndTheme() {
        assertValidationFailure(new SettingsPatch(new StudyPolicy(14, 0, List.of()), null, null, null));
        assertValidationFailure(new SettingsPatch(new StudyPolicy(60, 2, List.of("SUNDAY")), null, null, null));
        assertValidationFailure(new SettingsPatch(new StudyPolicy(60, 2, List.of("SUNDAY", "SUNDAY")), null, null, null));
        assertValidationFailure(new SettingsPatch(null, "24:00", null, null));
        assertValidationFailure(new SettingsPatch(null, null, null, "AUTO"));
        assertValidationFailure(new SettingsPatch(null, null, null, null));
    }

    private static void assertValidationFailure(SettingsPatch patch) {
        assertThatThrownBy(() -> UserSettingsService.validate(patch))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }
}
