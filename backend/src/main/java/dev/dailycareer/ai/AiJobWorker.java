package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs one job per user at a time and records the measured cost of every billed call. */
@Component
class AiJobWorker {
    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final AiModelClient model;
    private final AiCostService costs;
    private final Map<String, AiJobHandler> handlers;

    AiJobWorker(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager, AiModelClient model,
                AiCostService costs, List<AiJobHandler> handlers) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
        this.model = model;
        this.costs = costs;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(AiJobHandler::jobType, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${daily-career.ai.poll-delay-ms:2000}")
    void poll() {
        if (handlers.isEmpty()) return;
        expireLeases();
        for (int index = 0; index < 5; index++) {
            Claimed claimed = tx.execute(status -> claim());
            if (claimed == null) return;
            run(claimed);
        }
    }

    private void expireLeases() {
        var stale = jdbc.queryForList("""
                update daily_career.ai_job set status='UNKNOWN',error_code='AI_PROVIDER_RESULT_UNKNOWN',lease_token=null,lease_until=null,
                    finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where status='RUNNING' and lease_until<clock_timestamp() and job_type = any(?) returning id
                """, Long.class, (Object) handlers.keySet().toArray(String[]::new));
        for (long id : stale) failSafely(id, "AI_PROVIDER_RESULT_UNKNOWN");
    }

    private Claimed claim() {
        var rows = jdbc.queryForList("""
                select id from daily_career.ai_job j
                where j.status='QUEUED' and j.job_type = any(?) and (j.next_attempt_at is null or j.next_attempt_at<=clock_timestamp())
                  and not exists (select 1 from daily_career.ai_job r where r.user_id=j.user_id and r.status='RUNNING')
                order by j.id limit 1 for update skip locked
                """, Long.class, (Object) handlers.keySet().toArray(String[]::new));
        if (rows.isEmpty()) return null;
        String lease = UUID.randomUUID().toString();
        Map<String, Object> row = jdbc.queryForMap("""
                update daily_career.ai_job set status='RUNNING',attempt_count=attempt_count+1,lease_token=?,
                    lease_until=clock_timestamp()+interval '4 minutes',updated_at=clock_timestamp(),revision=revision+1
                where id=? returning id,user_id,job_type,model,attempt_count,input_snapshot::text as input
                """, lease, rows.getFirst());
        try {
            var job = new AiJobHandler.Job(((Number) row.get("id")).longValue(), ((Number) row.get("user_id")).longValue(),
                    row.get("job_type").toString(), json.readTree(row.get("input").toString()));
            return new Claimed(job, row.get("model").toString(), ((Number) row.get("attempt_count")).intValue(), lease);
        } catch (Exception invalid) { throw new IllegalStateException("Invalid AI job input", invalid); }
    }

    private void run(Claimed claimed) {
        AiJobHandler handler = handlers.get(claimed.job().jobType());
        AiCostService.Price price = costs.price();
        if (price == null) { finish(claimed, "FAILED", "AI_PRICE_UNAVAILABLE", null, null); return; }
        AiModelClient.Completion completion;
        try {
            completion = model.complete(claimed.model(), handler.request(claimed.job()));
        } catch (OpenAiResponses.IncompleteException incomplete) {
            finish(claimed, "FAILED", "AI_OUTPUT_INCOMPLETE", new Usage(incomplete.requestId(), incomplete.tokens(), price), null);
            return;
        } catch (OpenAiResponses.QuotaExceededException quota) {
            finish(claimed, "FAILED", "AI_PROVIDER_QUOTA_EXHAUSTED", null, null);
            return;
        } catch (IOException | InterruptedException unknown) {
            if (unknown instanceof InterruptedException) Thread.currentThread().interrupt();
            finish(claimed, "UNKNOWN", "AI_PROVIDER_RESULT_UNKNOWN", null, null);
            return;
        } catch (RuntimeException failure) {
            log.warn("AI job {} failed before a usable response: {}", claimed.job().id(), failure.getClass().getSimpleName());
            finish(claimed, "FAILED", "AI_PROVIDER_ERROR", null, null);
            return;
        }
        var usage = new Usage(completion.requestId(), completion.tokens(), price);
        try {
            finish(claimed, "SUCCEEDED", null, usage, completion.result());
        } catch (IllegalArgumentException rejected) {
            log.info("AI job {} result rejected: {}", claimed.job().id(), rejected.getMessage());
            finish(claimed, "FAILED", "AI_QUALITY_REJECTED", usage, null);
        }
    }

    /** Status, usage and the feature result commit together; a rejected result rolls back and is retried as FAILED. */
    private void finish(Claimed claimed, String status, String errorCode, Usage usage, JsonNode result) {
        AiJobHandler handler = handlers.get(claimed.job().jobType());
        tx.executeWithoutResult(transaction -> {
            int updated = jdbc.update("""
                    update daily_career.ai_job set status=?,error_code=?,result_payload=?::jsonb,
                        reserved_amount=case when ?='UNKNOWN' then reserved_amount else 0 end,
                        finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1,lease_token=null,lease_until=null
                    where id=? and status='RUNNING' and lease_token=?
                    """, status, errorCode, result == null ? null : result.toString(), status, claimed.job().id(), claimed.lease());
            if (updated != 1) return;
            if (usage != null) record(claimed, usage);
            if (result != null) handler.complete(claimed.job(), result);
            else handler.fail(claimed.job(), errorCode);
        });
    }

    private void record(Claimed claimed, Usage usage) {
        Map<String, Object> budget = jdbc.queryForMap("""
                select b.id,b.limit_amount,s.ai_fx_rate_krw from daily_career.ai_job j
                join daily_career.ai_budget b on b.id=j.budget_id join daily_career.user_setting s on s.user_id=j.user_id where j.id=?
                """, claimed.job().id());
        var cost = costs.cost(usage.price(), (BigDecimal) budget.get("ai_fx_rate_krw"), usage.tokens());
        long budgetId = ((Number) budget.get("id")).longValue();
        BigDecimal before = costs.committed(budgetId);
        jdbc.update("""
                insert into daily_career.ai_usage(user_id,ai_job_id,attempt_no,provider,provider_request_id,status,input_tokens,
                    cached_input_tokens,output_tokens,reasoning_tokens,provider_cost_usd,budget_cost_amount,fx_rate,pricing_snapshot)
                values (?,?,?,?,?,'KNOWN',?,?,?,?,?,?,?,?::jsonb)
                """, claimed.job().userId(), claimed.job().id(), claimed.attempt(), AiSettings.PROVIDER, usage.requestId(),
                usage.tokens().input(), usage.tokens().cachedInput(), usage.tokens().output(), usage.tokens().reasoning(),
                cost.usd(), cost.krw(), cost.fxRate(), cost.snapshot());
        notifyThresholds(claimed.job().userId(), budgetId, (BigDecimal) budget.get("limit_amount"), before, costs.committed(budgetId));
    }

    /** One notification per month when spending first reaches 50% and 80%; normal use should never reach them. */
    private void notifyThresholds(long user, long budgetId, BigDecimal limit, BigDecimal before, BigDecimal after) {
        for (int percent : new int[]{50, 80}) {
            BigDecimal line = limit.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            if (before.compareTo(line) < 0 && after.compareTo(line) >= 0) jdbc.update("""
                    insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,aggregate_id,aggregate_revision,payload,available_at)
                    values (?,?,?,'AI_BUDGET',?,0,'{}'::jsonb,now()) on conflict (event_key) do nothing
                    """, user, "AI_BUDGET:" + budgetId + ":" + percent, "AI_BUDGET_" + percent, budgetId);
        }
    }

    private void failSafely(long id, String errorCode) {
        try {
            Map<String, Object> row = jdbc.queryForMap("select id,user_id,job_type,input_snapshot::text as input from daily_career.ai_job where id=?", id);
            AiJobHandler handler = handlers.get(row.get("job_type").toString());
            var job = new AiJobHandler.Job(id, ((Number) row.get("user_id")).longValue(), row.get("job_type").toString(),
                    json.readTree(row.get("input").toString()));
            tx.executeWithoutResult(status -> handler.fail(job, errorCode));
        } catch (Exception failure) {
            log.warn("AI job {} failure handling failed: {}", id, failure.getClass().getSimpleName());
        }
    }

    private record Claimed(AiJobHandler.Job job, String model, int attempt, String lease) {}
    private record Usage(String requestId, AiModelClient.Tokens tokens, AiCostService.Price price) {}
}
