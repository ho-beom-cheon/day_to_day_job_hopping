package dev.dailycareer.competency;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Picks today's competency (design §5). S1 uses priority × gap × review timing plus the diversity penalty;
 * the weekly format quota arrives with the REVIEW format in P2.
 */
@Component
class TaskSelector {
    private final JdbcTemplate jdbc;
    private final CompetencyStore store;

    TaskSelector(JdbcTemplate jdbc, CompetencyStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    record Selection(CompetencyStore.Competency competency, String format, int difficulty, String reason) {}

    Selection select(long user, Collection<Long> excluded) {
        List<Map<String, Object>> recent = jdbc.queryForList("""
                select c.area,t.format from daily_career.daily_task t join daily_career.competency c on c.id=t.competency_id
                where t.user_id=? and t.status='COMPLETED' order by t.completed_at desc limit 2
                """, user);
        Selection best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (long id : store.readyCompetencies()) {
            if (excluded.contains(id)) continue;
            CompetencyStore.Competency competency = store.competency(id);
            var states = jdbc.queryForList("select level_estimate,self_level,next_due_at,evidence_count from daily_career.user_competency where user_id=? and competency_id=?",
                    user, id);
            Map<String, Object> state = states.isEmpty() ? Map.of() : states.getFirst();
            double estimate = state.get("level_estimate") instanceof BigDecimal value ? value.doubleValue()
                    : state.get("self_level") instanceof Number value ? value.doubleValue() : 1.0;
            OffsetDateTime due = state.get("next_due_at") instanceof OffsetDateTime value ? value
                    : state.get("next_due_at") instanceof java.sql.Timestamp value ? value.toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
            boolean dueNow = due == null || !due.isAfter(OffsetDateTime.now());
            String format = format(competency);
            double score = weight(competency.priority()) * Math.max(0.5, competency.targetLevel() - estimate) * (dueNow ? 1.5 : 0.6);
            for (Map<String, Object> previous : recent) {
                if (competency.area().equals(previous.get("area"))) score -= 1.0;
                if (format.equals(previous.get("format"))) score -= 0.5;
            }
            if (score > bestScore) {
                bestScore = score;
                int difficulty = Math.max(3, Math.min(5, (int) Math.floor(estimate) + 1));
                boolean fresh = state.isEmpty() || ((Number) state.getOrDefault("evidence_count", 0)).intValue() == 0;
                String reason = competency.code() + " · 우선순위 " + competency.priority() + " · "
                        + (fresh ? "아직 기록이 없어 실제 수준을 확인해요" : "추정 수준 " + String.format("%.1f", estimate) + " → 목표 " + competency.targetLevel())
                        + (dueNow && !fresh ? " · 복습 시점이 됐어요" : "");
                best = new Selection(competency, format, difficulty, reason);
            }
        }
        if (best == null) throw new ApiException(ApiErrorCode.COMPETENCY_NOT_READY);
        return best;
    }

    static String format(CompetencyStore.Competency competency) { return "I".equals(competency.area()) ? "INTERVIEW" : "CASE"; }

    private static double weight(String priority) {
        return switch (priority) { case "P1" -> 3; case "P2" -> 2; default -> 1; };
    }
}
