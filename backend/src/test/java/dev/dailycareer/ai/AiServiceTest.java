package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.user.UserAccountService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserAccountService users = mock(UserAccountService.class);
    private final AiService service = new AiService(jdbc, new ObjectMapper(), users,
            mock(IdempotencyService.class), mock(AiProvider.class),mock(AiLessonCatalog.class));

    @Test
    void listBuildsSqlWithoutUntypedNullParameters() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        var result = service.list(7, null, null, 0, 20);

        verify(jdbc).queryForList(
                "select * from daily_career.ai_job where user_id=? order by created_at desc,id desc",
                new Object[]{7L});
        assertThat(result.get("totalElements")).isEqualTo(0);
    }

    @Test
    void listAddsOnlyProvidedFilters() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.list(7, "DAILY_FEEDBACK", null, 0, 20);
        service.list(7, null, "SUCCEEDED", 0, 20);

        verify(jdbc).queryForList(
                "select * from daily_career.ai_job where user_id=? and job_type=? order by created_at desc,id desc",
                new Object[]{7L, "DAILY_FEEDBACK"});
        verify(jdbc).queryForList(
                "select * from daily_career.ai_job where user_id=? and status=? order by created_at desc,id desc",
                new Object[]{7L, "SUCCEEDED"});
    }
}
