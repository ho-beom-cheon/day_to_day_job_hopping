package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Queues AI jobs only after the monthly hard limit admits the worst-case cost of the call. */
@Service
public class AiJobService {
    static final int DAILY_CALL_LIMIT = 60;
    static final int OPEN_JOB_LIMIT = 3;
    private final JdbcTemplate jdbc;
    private final AiSettings settings;
    private final AiCostService costs;

    AiJobService(JdbcTemplate jdbc, AiSettings settings, AiCostService costs) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.costs = costs;
    }

    public record Queued(long jobId, boolean reused) {}

    /**
     * Joins the caller's transaction so the job and the business row that waits for it commit together.
     * Throws AI_DISABLED, AI_PRICE_UNAVAILABLE, AI_BUDGET_EXCEEDED, AI_REQUEST_LIMIT_EXCEEDED or AI_CONCURRENT_JOB_LIMIT;
     * callers fall back to the AI-free path on any of them.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public Queued enqueue(long user, String jobType, String promptVersion, JsonNode input, String dedupeKey,
                          long estimatedInputTokens, int maxOutputTokens) {
        if (!settings.enabled()) throw new ApiException(ApiErrorCode.AI_DISABLED);
        AiCostService.Price price = costs.price();
        if (price == null) throw new ApiException(ApiErrorCode.AI_PRICE_UNAVAILABLE);
        AiCostService.Budget budget = costs.lockMonthlyBudget(user);
        var existing = jdbc.queryForList("select id from daily_career.ai_job where user_id=? and dedupe_key=?", Long.class, user, dedupeKey);
        if (!existing.isEmpty()) return new Queued(existing.getFirst(), true);
        Integer today = jdbc.queryForObject("""
                select count(*)::int from daily_career.ai_job
                where user_id=? and created_at>=(?::date)::timestamp at time zone 'Asia/Seoul'
                """, Integer.class, user, LocalDate.now(AiCostService.SEOUL));
        if (today != null && today >= DAILY_CALL_LIMIT) throw new ApiException(ApiErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
        Integer open = jdbc.queryForObject("select count(*)::int from daily_career.ai_job where user_id=? and status in ('QUEUED','RUNNING')",
                Integer.class, user);
        if (open != null && open >= OPEN_JOB_LIMIT) throw new ApiException(ApiErrorCode.AI_CONCURRENT_JOB_LIMIT);
        BigDecimal reserve = AiCostService.reservation(price, budget.fxRate(), estimatedInputTokens, maxOutputTokens);
        if (costs.committed(budget.id()).add(reserve).compareTo(budget.limit()) > 0) throw new ApiException(ApiErrorCode.AI_BUDGET_EXCEEDED);
        Long id = jdbc.queryForObject("""
                insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,model,prompt_version,input_snapshot,reserved_amount)
                values (?,?,?,?,'QUEUED',?,?,?,?::jsonb,?) returning id
                """, Long.class, user, budget.id(), jobType, dedupeKey, AiSettings.PROVIDER, settings.model(), promptVersion,
                input.toString(), reserve);
        if (id == null) throw new IllegalStateException("AI job id was not returned");
        return new Queued(id, false);
    }

    /** Rough upper estimate for Korean-heavy JSON prompts: about one token per two characters. */
    public static long estimateTokens(String... parts) {
        long characters = 0;
        for (String part : parts) characters += part == null ? 0 : part.length();
        return characters / 2 + 200;
    }
}
