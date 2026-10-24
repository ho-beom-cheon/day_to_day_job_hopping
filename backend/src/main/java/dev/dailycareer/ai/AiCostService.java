package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly hard limit (design §7.2) and per-call cost recording (§7.3).
 * Costs are computed only from a verified price row; without one, AI calls are refused.
 */
@Service
public class AiCostService {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final String FORMULA_VERSION = "openai-responses-v1";
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiSettings settings;

    AiCostService(JdbcTemplate jdbc, ObjectMapper json, AiSettings settings) {
        this.jdbc = jdbc;
        this.json = json;
        this.settings = settings;
    }

    public record Price(long id, BigDecimal input, BigDecimal cachedInput, BigDecimal output, LocalDate verifiedAt, String sourceUrl) {}
    record Budget(long id, BigDecimal limit, BigDecimal fxRate) {}
    record Cost(BigDecimal usd, BigDecimal krw, BigDecimal fxRate, String snapshot) {}

    public Price price() {
        var rows = jdbc.query("""
                select id,input_usd_per_1m,cached_input_usd_per_1m,output_usd_per_1m,verified_at,source_url
                from daily_career.ai_model_price
                where provider=? and model=? and effective_from<=current_date
                order by effective_from desc limit 1
                """, (row, number) -> new Price(row.getLong(1), row.getBigDecimal(2), row.getBigDecimal(3), row.getBigDecimal(4),
                row.getObject(5, LocalDate.class), row.getString(6)), AiSettings.PROVIDER, settings.model());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Creates or refreshes the calendar-month budget row and locks it for the reservation check. */
    Budget lockMonthlyBudget(long user) {
        LocalDate start = LocalDate.now(SEOUL).withDayOfMonth(1);
        Map<String, Object> setting = jdbc.queryForMap(
                "select ai_monthly_limit_krw,ai_fx_rate_krw from daily_career.user_setting where user_id=?", user);
        BigDecimal limit = (BigDecimal) setting.get("ai_monthly_limit_krw");
        jdbc.update("""
                insert into daily_career.ai_budget(user_id,period_start,period_end,currency,limit_amount,hard_limit_enabled)
                values (?,?,?,'KRW',?,true) on conflict (user_id,period_start,period_end) do nothing
                """, user, start, start.plusMonths(1), limit);
        Map<String, Object> row = jdbc.queryForMap("""
                select id,limit_amount from daily_career.ai_budget
                where user_id=? and period_start=? and period_end=? for update
                """, user, start, start.plusMonths(1));
        long id = ((Number) row.get("id")).longValue();
        if (((BigDecimal) row.get("limit_amount")).compareTo(limit) != 0) {
            jdbc.update("update daily_career.ai_budget set limit_amount=?,updated_at=clock_timestamp(),revision=revision+1 where id=?", limit, id);
        }
        return new Budget(id, limit, (BigDecimal) setting.get("ai_fx_rate_krw"));
    }

    /** Spent plus outstanding reservations. Results-unknown calls stay reserved because they may have been billed. */
    BigDecimal committed(long budgetId) {
        BigDecimal spent = jdbc.queryForObject("""
                select coalesce(sum(u.budget_cost_amount),0) from daily_career.ai_usage u
                join daily_career.ai_job j on j.id=u.ai_job_id where j.budget_id=?
                """, BigDecimal.class, budgetId);
        BigDecimal reserved = jdbc.queryForObject("""
                select coalesce(sum(reserved_amount),0) from daily_career.ai_job
                where budget_id=? and status in ('QUEUED','RUNNING','UNKNOWN')
                """, BigDecimal.class, budgetId);
        return spent.add(reserved);
    }

    /** Worst-case cost: every estimated input token uncached and the full output cap used. */
    static BigDecimal reservation(Price price, BigDecimal fxRate, long inputTokens, int maxOutputTokens) {
        BigDecimal usd = price.input().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.output().multiply(BigDecimal.valueOf(maxOutputTokens)))
                .divide(MILLION, 8, RoundingMode.UP);
        return usd.multiply(fxRate).setScale(6, RoundingMode.UP);
    }

    Cost cost(Price price, BigDecimal fxRate, AiModelClient.Tokens tokens) {
        BigDecimal usd = price.input().multiply(BigDecimal.valueOf(tokens.input() - tokens.cachedInput()))
                .add(price.cachedInput().multiply(BigDecimal.valueOf(tokens.cachedInput())))
                .add(price.output().multiply(BigDecimal.valueOf(tokens.output())))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("priceId", price.id());
        snapshot.put("inputUsdPer1m", price.input());
        snapshot.put("cachedInputUsdPer1m", price.cachedInput());
        snapshot.put("outputUsdPer1m", price.output());
        snapshot.put("priceVerifiedAt", price.verifiedAt().toString());
        snapshot.put("sourceUrl", price.sourceUrl());
        snapshot.put("fxRateKrw", fxRate);
        snapshot.put("formula", FORMULA_VERSION);
        snapshot.put("reasoningCountedAsOutput", true);
        try {
            return new Cost(usd, usd.multiply(fxRate).setScale(6, RoundingMode.HALF_UP), fxRate, json.writeValueAsString(snapshot));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public record UsageSummary(boolean enabled, String model, LocalDate periodStart, BigDecimal limitKrw, BigDecimal spentKrw,
                               BigDecimal reservedKrw, BigDecimal remainingKrw, BigDecimal sixMonthSpentKrw, BigDecimal fxRateKrw,
                               LocalDate priceVerifiedAt, List<TypeUsage> byJobType) {}
    public record TypeUsage(String jobType, long calls, BigDecimal spentKrw) {}

    @Transactional
    public UsageSummary summary(long user) {
        Budget budget = lockMonthlyBudget(user);
        BigDecimal spent = jdbc.queryForObject("""
                select coalesce(sum(u.budget_cost_amount),0) from daily_career.ai_usage u
                join daily_career.ai_job j on j.id=u.ai_job_id where j.budget_id=?
                """, BigDecimal.class, budget.id());
        BigDecimal committed = committed(budget.id());
        BigDecimal sixMonths = jdbc.queryForObject("""
                select coalesce(sum(budget_cost_amount),0) from daily_career.ai_usage
                where user_id=? and created_at>=date_trunc('month', now() at time zone 'Asia/Seoul') - interval '5 months'
                """, BigDecimal.class, user);
        List<TypeUsage> types = jdbc.query("""
                select j.job_type,count(*),coalesce(sum(u.budget_cost_amount),0) from daily_career.ai_job j
                left join daily_career.ai_usage u on u.ai_job_id=j.id where j.budget_id=? group by j.job_type order by j.job_type
                """, (row, number) -> new TypeUsage(row.getString(1), row.getLong(2), row.getBigDecimal(3)), budget.id());
        Price price = price();
        return new UsageSummary(settings.enabled() && price != null, settings.model(), LocalDate.now(SEOUL).withDayOfMonth(1),
                budget.limit(), spent, committed.subtract(spent), budget.limit().subtract(committed).max(BigDecimal.ZERO),
                sixMonths, budget.fxRate(), price == null ? null : price.verifiedAt(), types);
    }
}
