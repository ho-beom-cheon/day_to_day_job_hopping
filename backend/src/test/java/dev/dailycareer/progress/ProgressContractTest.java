package dev.dailycareer.progress;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressContractTest {
    @Test void resolvesIsoWeekToMonday() {
        assertThat(ProgressService.isoWeekStart("2026-W39")).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test void rejectsMalformedOrNonexistentIsoWeek() {
        assertValidationFailure("2026-W00");
        assertValidationFailure("2025-W53");
        assertValidationFailure("2026-39");
        assertValidationFailure(null);
    }

    private static void assertValidationFailure(String week) {
        assertThatThrownBy(() -> ProgressService.isoWeekStart(week))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }
}
