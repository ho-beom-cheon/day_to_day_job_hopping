package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.ai.AiJobHandler;
import dev.dailycareer.ai.AiJobService;
import dev.dailycareer.ai.AiRequest;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Career facts and resume claims (design §3.3, §4.8). Only CONFIRMED facts and pasted claim sentences reach the AI;
 * resume files, contact details and other personal data are never stored.
 */
@Service
public class CareerFacts {
    static final String REVIEW_JOB = "RESUME_CLAIM_REVIEW";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountService users;
    private final AiJobService ai;
    private final LoopPrompts prompts;
    private final CompetencyStore store;
    private final DailyTaskService tasks;

    CareerFacts(JdbcTemplate jdbc, ObjectMapper json, UserAccountService users, AiJobService ai, LoopPrompts prompts,
                CompetencyStore store, DailyTaskService tasks) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.ai = ai;
        this.prompts = prompts;
        this.store = store;
        this.tasks = tasks;
    }

    public record Fact(@JsonId Long factId, String projectName, String clientName, String periodFrom, String periodTo, String roleText,
                       String techText, String confirmedScope, String cautionText, String status, OffsetDateTime confirmedAt, int revision) {}
    public record FactRequest(String projectName, String clientName, String periodFrom, String periodTo, String roleText,
                              String techText, String confirmedScope, String cautionText) {}
    public record Claim(@JsonId Long claimId, @JsonId Long careerFactId, String claimText, boolean metricConfirmed, String reviewStatus,
                        JsonNode review, boolean reviewPending, String revisedText, int revision) {}
    public record ClaimRequest(String careerFactId, String claimText) {}
    public record ClaimPatch(Boolean metricConfirmed, String revisedText) {}

    @Transactional(readOnly = true)
    public List<Fact> facts(long user) {
        users.current(user);
        return jdbc.query("select * from daily_career.career_fact where user_id=? order by period_from desc,id desc", this::fact, user);
    }

    @Transactional
    public Fact createFact(long user, FactRequest request) {
        users.current(user);
        validate(request);
        Long id = jdbc.queryForObject("""
                insert into daily_career.career_fact(user_id,project_name,client_name,period_from,period_to,role_text,tech_text,confirmed_scope,caution_text)
                values (?,?,?,?,?,?,?,?,?) returning id
                """, Long.class, user, request.projectName().strip(), blankToNull(request.clientName()), request.periodFrom(),
                blankToNull(request.periodTo()), text(request.roleText()), text(request.techText()), text(request.confirmedScope()), text(request.cautionText()));
        return fact(user, id);
    }

    /** Editing a confirmed fact sends it back to CANDIDATE so the learner re-confirms what the AI may use. */
    @Transactional
    public Fact updateFact(long user, long id, FactRequest request, int expected) {
        users.current(user);
        validate(request);
        int changed = jdbc.update("""
                update daily_career.career_fact set project_name=?,client_name=?,period_from=?,period_to=?,role_text=?,tech_text=?,
                    confirmed_scope=?,caution_text=?,status='CANDIDATE',confirmed_at=null,updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and revision=?
                """, request.projectName().strip(), blankToNull(request.clientName()), request.periodFrom(), blankToNull(request.periodTo()),
                text(request.roleText()), text(request.techText()), text(request.confirmedScope()), text(request.cautionText()), id, user, expected);
        if (changed != 1) throw notFoundOrStale("career_fact", user, id);
        return fact(user, id);
    }

    @Transactional
    public Fact confirmFact(long user, long id, int expected) {
        users.current(user);
        int changed = jdbc.update("""
                update daily_career.career_fact set status='CONFIRMED',confirmed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and revision=? and confirmed_scope<>''
                """, id, user, expected);
        if (changed != 1) {
            var rows = jdbc.queryForList("select confirmed_scope from daily_career.career_fact where id=? and user_id=?", String.class, id, user);
            if (!rows.isEmpty() && rows.getFirst().isBlank()) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
            throw notFoundOrStale("career_fact", user, id);
        }
        return fact(user, id);
    }

    @Transactional(readOnly = true)
    public List<Claim> claims(long user) {
        users.current(user);
        return jdbc.query("select * from daily_career.resume_claim where user_id=? order by id desc", (row, number) -> claim(row), user);
    }

    @Transactional
    public Claim createClaim(long user, ClaimRequest request) {
        users.current(user);
        String text = request.claimText() == null ? "" : request.claimText().strip();
        if (text.isEmpty() || text.length() > 2000) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        Long fact = request.careerFactId() == null || request.careerFactId().isBlank() ? null : dev.dailycareer.common.json.Ids.parse(request.careerFactId());
        if (fact != null) fact(user, fact);
        Long id = jdbc.queryForObject("insert into daily_career.resume_claim(user_id,career_fact_id,claim_text) values (?,?,?) returning id",
                Long.class, user, fact, text);
        return claim(user, id);
    }

    @Transactional
    public Claim patchClaim(long user, long id, ClaimPatch patch, int expected) {
        users.current(user);
        if (patch.revisedText() != null && patch.revisedText().length() > 2000) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        int changed = jdbc.update("""
                update daily_career.resume_claim set metric_confirmed=coalesce(?,metric_confirmed),revised_text=coalesce(?,revised_text),
                    updated_at=clock_timestamp(),revision=revision+1 where id=? and user_id=? and revision=?
                """, patch.metricConfirmed(), patch.revisedText() == null ? null : patch.revisedText().strip(), id, user, expected);
        if (changed != 1) throw notFoundOrStale("resume_claim", user, id);
        return claim(user, id);
    }

    /** Queues the AI pre-check; throws the AI_* code when the budget or configuration does not allow it. */
    @Transactional(noRollbackFor = ApiException.class)
    public Claim review(long user, long id) {
        Claim claim = claim(user, id);
        ObjectNode input = json.createObjectNode().put("claimId", id);
        var queued = ai.enqueue(user, REVIEW_JOB, LoopPrompts.VERSION, input, "claim-review:" + id + ":" + claim.revision(),
                AiJobService.estimateTokens(prompts.overhead(LoopPrompts.Role.CLAIM_REVIEW), effectiveText(claim), "fact"),
                LoopPrompts.Role.CLAIM_REVIEW.maxOutputTokens);
        jdbc.update("update daily_career.resume_claim set review_job_id=? where id=?", queued.jobId(), id);
        return claim(user, id);
    }

    /** Starts a pressure-style INTERVIEW task about the claim (design §4.8). */
    @Transactional
    public DailyTaskViews.Task interview(long user, long id) {
        Claim claim = claim(user, id);
        var open = jdbc.queryForList("select id from daily_career.daily_task where user_id=? and status = any(?)", Long.class, user,
                (Object) DailyTaskService.OPEN.toArray(String[]::new));
        if (!open.isEmpty()) throw new ApiException(ApiErrorCode.TASK_ALREADY_OPEN);
        if (claim.careerFactId() != null) {
            Fact fact = fact(user, claim.careerFactId());
            if (!"CONFIRMED".equals(fact.status())) throw new ApiException(ApiErrorCode.CAREER_FACT_NOT_CONFIRMED);
        }
        CompetencyStore.Competency competency = store.byCode("I1");
        if (competency == null || store.currentSheet(competency.id()) == null) throw new ApiException(ApiErrorCode.COMPETENCY_NOT_READY);
        var selection = new TaskSelector.Selection(competency, "INTERVIEW", 4, "I1 · 이력서 문장 검증 · 압박형 면접");
        return tasks.view(user, tasks.create(user, selection, null, id));
    }

    /** The sentence the AI works from: the learner's applied revision when present, otherwise the original claim. */
    static final String EFFECTIVE_CLAIM_TEXT = "coalesce(nullif(btrim(c.revised_text),''),c.claim_text)";

    private static String effectiveText(Claim claim) {
        return claim.revisedText() == null || claim.revisedText().isBlank() ? claim.claimText() : claim.revisedText().strip();
    }

    static ObjectNode factInput(ObjectMapper json, Map<String, Object> row) {
        return json.createObjectNode().put("projectName", String.valueOf(row.get("project_name")))
                .put("client", row.get("client_name") == null ? "" : row.get("client_name").toString())
                .put("period", row.get("period_from") + " ~ " + (row.get("period_to") == null ? "진행 중" : row.get("period_to")))
                .put("role", String.valueOf(row.get("role_text"))).put("tech", String.valueOf(row.get("tech_text")))
                .put("confirmedScope", String.valueOf(row.get("confirmed_scope"))).put("caution", String.valueOf(row.get("caution_text")));
    }

    Fact fact(long user, long id) {
        var rows = jdbc.query("select * from daily_career.career_fact where id=? and user_id=?", this::fact, id, user);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    Claim claim(long user, long id) {
        users.current(user);
        var rows = jdbc.query("select * from daily_career.resume_claim where id=? and user_id=?", (row, number) -> claim(row), id, user);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Fact fact(java.sql.ResultSet row, int number) throws java.sql.SQLException {
        return new Fact(row.getLong("id"), row.getString("project_name"), row.getString("client_name"), row.getString("period_from"),
                row.getString("period_to"), row.getString("role_text"), row.getString("tech_text"), row.getString("confirmed_scope"),
                row.getString("caution_text"), row.getString("status"), row.getObject("confirmed_at", OffsetDateTime.class), row.getInt("revision"));
    }

    private Claim claim(java.sql.ResultSet row) throws java.sql.SQLException {
        long job = row.getLong("review_job_id");
        boolean pending = !row.wasNull() && Boolean.TRUE.equals(jdbc.queryForObject(
                "select status in ('QUEUED','RUNNING') from daily_career.ai_job where id=?", Boolean.class, job));
        String review = row.getString("review_payload");
        long fact = row.getLong("career_fact_id");
        boolean noFact = row.wasNull();
        try {
            return new Claim(row.getLong("id"), noFact ? null : fact, row.getString("claim_text"), row.getBoolean("metric_confirmed"),
                    row.getString("review_status"), review == null ? null : json.readTree(review), pending, row.getString("revised_text"), row.getInt("revision"));
        } catch (Exception invalid) { throw new IllegalStateException(invalid); }
    }

    private ApiException notFoundOrStale(String table, long user, long id) {
        return new ApiException(Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from daily_career." + table + " where id=? and user_id=?)", Boolean.class, id, user))
                ? ApiErrorCode.PRECONDITION_FAILED : ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    private static void validate(FactRequest request) {
        if (request.projectName() == null || request.projectName().isBlank() || request.projectName().length() > 200
                || request.periodFrom() == null || !request.periodFrom().matches("[0-9]{4}-[0-9]{2}")
                || request.periodTo() != null && !request.periodTo().isBlank() && !request.periodTo().matches("[0-9]{4}-[0-9]{2}")
                || length(request.clientName()) > 120 || length(request.roleText()) > 2000 || length(request.techText()) > 500
                || length(request.confirmedScope()) > 4000 || length(request.cautionText()) > 2000)
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
    }

    private static int length(String value) { return value == null ? 0 : value.length(); }
    private static String text(String value) { return value == null ? "" : value.strip(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    static int revision(jakarta.servlet.http.HttpServletRequest request) { return Revisions.required(request); }

    @Component
    static class ReviewHandler implements AiJobHandler {
        private final JdbcTemplate jdbc;
        private final ObjectMapper json;
        private final LoopPrompts prompts;

        ReviewHandler(JdbcTemplate jdbc, ObjectMapper json, LoopPrompts prompts) {
            this.jdbc = jdbc;
            this.json = json;
            this.prompts = prompts;
        }

        @Override public String jobType() { return REVIEW_JOB; }

        @Override public AiRequest request(Job job) {
            Map<String, Object> row = jdbc.queryForMap("""
                    select %s as claim_text,f.project_name,f.client_name,f.period_from,f.period_to,f.role_text,f.tech_text,f.confirmed_scope,f.caution_text,f.status
                    from daily_career.resume_claim c left join daily_career.career_fact f on f.id=c.career_fact_id and f.user_id=c.user_id
                    where c.id=? and c.user_id=?
                    """.formatted(EFFECTIVE_CLAIM_TEXT), job.input().path("claimId").asLong(), job.userId());
            ObjectNode input = json.createObjectNode().put("claim", row.get("claim_text").toString());
            if ("CONFIRMED".equals(row.get("status"))) input.set("fact", factInput(json, row));
            else input.putNull("fact");
            return prompts.request(LoopPrompts.Role.CLAIM_REVIEW, input);
        }

        @Override public void complete(Job job, JsonNode result) {
            String status = result.path("status").asText();
            if (!List.of("NEEDS_FIX", "NEEDS_EVIDENCE", "OK").contains(status)) throw new IllegalArgumentException("Unknown review status");
            jdbc.update("update daily_career.resume_claim set review_status=?,review_payload=?::jsonb,updated_at=clock_timestamp() where id=? and user_id=?",
                    status, result.toString(), job.input().path("claimId").asLong(), job.userId());
        }

        @Override public void fail(Job job, String errorCode) {
            jdbc.update("update daily_career.resume_claim set review_payload=?::jsonb,updated_at=clock_timestamp() where id=? and user_id=?",
                    json.createObjectNode().put("errorCode", errorCode).toString(), job.input().path("claimId").asLong(), job.userId());
        }
    }
}
