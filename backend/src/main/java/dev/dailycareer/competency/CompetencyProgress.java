package dev.dailycareer.competency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Estimated level and review timing per competency (design §5, §5.1). Always recomputed from stored results. */
@Component
class CompetencyProgress {
    private final JdbcTemplate jdbc;
    CompetencyProgress(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record Evidence(int score, int difficulty, boolean self) {}

    void recompute(long user, long competency) {
        List<Evidence> recent = jdbc.query("""
                select r.final_score,b.difficulty,r.status='SELF_GRADED' from daily_career.daily_task t
                join daily_career.daily_task_result r on r.task_id=t.id
                join daily_career.task_bank_item b on b.id=t.bank_item_id
                where t.user_id=? and t.competency_id=? and t.status='COMPLETED' and r.status<>'HOLD' and not r.score_excluded
                order by t.completed_at desc limit 3
                """, (row, number) -> new Evidence(row.getInt(1), row.getInt(2), row.getBoolean(3)), user, competency);
        Integer evidence = jdbc.queryForObject("""
                select count(*)::int from daily_career.daily_task t join daily_career.daily_task_result r on r.task_id=t.id
                where t.user_id=? and t.competency_id=? and t.status='COMPLETED' and r.status<>'HOLD' and not r.score_excluded
                """, Integer.class, user, competency);
        BigDecimal estimate = estimate(recent, evidence == null ? 0 : evidence);
        Integer last = recent.isEmpty() ? null : recent.getFirst().score();
        Integer previous = recent.size() > 1 ? recent.get(1).score() : null;
        jdbc.update("""
                insert into daily_career.user_competency(user_id,competency_id,level_estimate,evidence_count,last_score,next_due_at)
                values (?,?,?,?,?,now()+make_interval(days => ?))
                on conflict (user_id,competency_id) do update set level_estimate=excluded.level_estimate,
                    evidence_count=excluded.evidence_count,last_score=excluded.last_score,next_due_at=excluded.next_due_at,
                    updated_at=clock_timestamp(),revision=daily_career.user_competency.revision+1
                """, user, competency, estimate, evidence == null ? 0 : evidence, last, last == null ? 0 : interval(last, previous));
    }

    /**
     * Each result maps to an achieved level: difficulty 3 at 70 points is level 2, at 100 points level 3.
     * Recent results weigh 3:2:1, self-graded ones ×0.7; a level above 2 needs at least two results.
     */
    static BigDecimal estimate(List<Evidence> recent, int evidence) {
        if (recent.isEmpty()) return null;
        double weighted = 0, weights = 0;
        for (int index = 0; index < recent.size(); index++) {
            Evidence item = recent.get(index);
            double achieved = Math.max(1, Math.min(4, item.difficulty() - 1 + (item.score() - 70) / 30.0));
            double weight = (3 - index) * (item.self() ? 0.7 : 1.0);
            weighted += achieved * weight;
            weights += weight;
        }
        double value = weighted / weights;
        if (evidence < 2) value = Math.min(value, 2.0);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Below 60 → 2 days, 60–79 → 5 days, 80+ → 12 days, doubled after two strong results in a row. */
    static int interval(int score, Integer previous) {
        if (score < 60) return 2;
        if (score < 80) return 5;
        return previous != null && previous >= 80 ? 24 : 12;
    }
}
