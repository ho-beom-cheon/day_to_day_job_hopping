package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.ai.AiCostService;
import dev.dailycareer.ai.AiJobService;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.competency.DailyTaskViews.*;

/** The daily loop (design §4.2): one open task per user, AI grading with an AI-free fallback at every step. */
@Service
public class DailyTaskService {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final Set<String> OPEN = Set.of("PREPARING", "READY", "IN_PROGRESS", "GRADING", "FEEDBACK");
    static final String BANK_JOB = "TASK_BANK_GENERATE", GRADE_JOB = "DAILY_TASK_GRADE", FOLLOWUP_JOB = "DAILY_FOLLOWUP_TURN";
    private static final int ANSWER_LIMIT = 8000;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountService users;
    private final CompetencyStore store;
    private final TaskSelector selector;
    private final CompetencyProgress progress;
    private final AiJobService ai;
    private final AiCostService costs;
    private final LoopPrompts prompts;

    DailyTaskService(JdbcTemplate jdbc, ObjectMapper json, UserAccountService users, CompetencyStore store, TaskSelector selector,
                     CompetencyProgress progress, AiJobService ai, AiCostService costs, LoopPrompts prompts) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.store = store;
        this.selector = selector;
        this.progress = progress;
        this.ai = ai;
        this.costs = costs;
        this.prompts = prompts;
    }

    // ---- Today

    @Transactional
    public Today today(long user) {
        users.current(user);
        var open = openTaskId(user);
        TaskSummary summary = open == null ? null : summary(open);
        LocalDate monday = LocalDate.now(SEOUL).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Integer completed = jdbc.queryForObject("select count(*)::int from daily_career.daily_task where user_id=? and status='COMPLETED' and completed_on>=?",
                Integer.class, user, monday);
        Integer target = jdbc.queryForObject("select weekly_task_target::int from daily_career.user_setting where user_id=?", Integer.class, user);
        var usage = costs.summary(user);
        List<CardSummary> cards = jdbc.query("""
                select a.task_id,c.code,a.title,a.interview_line,a.updated_at from daily_career.answer_card a
                join daily_career.competency c on c.id=a.competency_id where a.user_id=? order by a.updated_at desc limit 3
                """, (row, number) -> new CardSummary(row.getLong(1), row.getString(2), row.getString(3), row.getString(4),
                row.getObject(5, java.time.OffsetDateTime.class)), user);
        return new Today(summary, new Week(target == null ? 4 : target, completed == null ? 0 : completed),
                new AiState(usage.enabled(), usage.limitKrw(), usage.remainingKrw()), cards);
    }

    /** Returns the open task, or selects and creates the next one ("오늘 과제 받기" / "한 과제 더"). */
    @Transactional
    public Task startToday(long user) {
        users.current(user);
        Long open = openTaskId(user);
        if (open != null) return view(user, open);
        return view(user, create(user, selector.select(user, Set.of()), null, null));
    }

    @Transactional
    public Task swap(long user, long taskId) {
        Map<String, Object> task = lock(user, taskId);
        if (!Set.of("PREPARING", "READY").contains(task.get("status").toString())) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        if (task.get("resume_claim_id") != null) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        long competency = ((Number) task.get("competency_id")).longValue();
        jdbc.update("update daily_career.daily_task set status='SWAPPED',updated_at=clock_timestamp(),revision=revision+1 where id=?", taskId);
        jdbc.update("""
                insert into daily_career.user_competency(user_id,competency_id,swap_count) values (?,?,1)
                on conflict (user_id,competency_id) do update set swap_count=daily_career.user_competency.swap_count+1,
                    updated_at=clock_timestamp(),revision=daily_career.user_competency.revision+1
                """, user, competency);
        Set<Long> excluded = new HashSet<>(jdbc.queryForList("""
                select competency_id from daily_career.daily_task
                where user_id=? and status='SWAPPED' and created_at>=(?::date)::timestamp at time zone 'Asia/Seoul'
                """, Long.class, user, LocalDate.now(SEOUL)));
        if (excluded.size() >= store.readyCompetencies().size()) excluded = Set.of(competency);
        return view(user, create(user, selector.select(user, excluded), taskId, null));
    }

    /** Creates a task and attaches a stored bank task, or queues generation when the bank is empty. */
    long create(long user, TaskSelector.Selection selection, Long swappedFrom, Long resumeClaimId) {
        Map<String, Object> setting = jdbc.queryForMap("select default_time_mode,default_grading_mode from daily_career.user_setting where user_id=?", user);
        CompetencyStore.Sheet sheet = store.currentSheet(selection.competency().id());
        if (sheet == null) throw new ApiException(ApiErrorCode.COMPETENCY_NOT_READY);
        Long item = resumeClaimId == null ? pickBankItem(user, selection, sheet.versionId()) : null;
        Long id = jdbc.queryForObject("""
                insert into daily_career.daily_task(user_id,competency_id,bank_item_id,resume_claim_id,format,time_mode,status,plan_reason,
                    grading_mode,swapped_from_task_id)
                values (?,?,?,?,?,?,?,?,?,?) returning id
                """, Long.class, user, selection.competency().id(), item, resumeClaimId, selection.format(), setting.get("default_time_mode"),
                item == null ? "PREPARING" : "READY", selection.reason(), setting.get("default_grading_mode"), swappedFrom);
        if (id == null) throw new IllegalStateException("Task id was not returned");
        if (item == null) queueGeneration(user, id, selection, sheet, resumeClaimId);
        return id;
    }

    private Long pickBankItem(long user, TaskSelector.Selection selection, long sheetVersion) {
        var rows = jdbc.queryForList("""
                select b.id from daily_career.task_bank_item b
                where b.competency_id=? and b.format=? and b.sheet_version_id=? and b.status='ACTIVE' and b.owner_user_id is null
                  and not exists (select 1 from daily_career.daily_task t where t.user_id=? and t.bank_item_id=b.id)
                order by abs(b.difficulty-?),b.id limit 1
                """, Long.class, selection.competency().id(), selection.format(), sheetVersion, user, selection.difficulty());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void queueGeneration(long user, long taskId, TaskSelector.Selection selection, CompetencyStore.Sheet sheet, Long claimId) {
        var existingTitles = jdbc.queryForList("""
                select task_payload->>'title' from daily_career.task_bank_item where competency_id=? order by id desc limit 30
                """, String.class, selection.competency().id());
        ObjectNode input = json.createObjectNode().put("taskId", taskId).put("competencyId", selection.competency().id())
                .put("sheetVersionId", sheet.versionId()).put("format", selection.format()).put("difficulty", selection.difficulty())
                .put("count", claimId == null ? 2 : 1);
        if (claimId != null) input.put("resumeClaimId", claimId);
        input.set("existingTitles", json.valueToTree(existingTitles));
        String key = claimId == null
                ? "bank:" + sheet.versionId() + ":" + selection.format() + ":" + selection.difficulty() + ":" + bankCount(selection, sheet.versionId())
                : "claim-task:" + claimId + ":" + taskId;
        try {
            var queued = ai.enqueue(user, BANK_JOB, LoopPrompts.VERSION, input, key,
                    AiJobService.estimateTokens(prompts.overhead(LoopPrompts.Role.TASK_GENERATE), sheet.document().body(), input.toString()),
                    LoopPrompts.Role.TASK_GENERATE.maxOutputTokens);
            jdbc.update("update daily_career.daily_task set generation_job_id=? where id=?", queued.jobId(), taskId);
            if (queued.reused()) attachFinishedBatch(user, taskId, queued.jobId());
        } catch (ApiException unavailable) {
            jdbc.update("update daily_career.daily_task set status='FAILED',failure_code=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",
                    unavailable.code().name(), taskId);
        }
    }

    private int bankCount(TaskSelector.Selection selection, long sheetVersion) {
        Integer count = jdbc.queryForObject("select count(*)::int from daily_career.task_bank_item where competency_id=? and format=? and sheet_version_id=? and owner_user_id is null",
                Integer.class, selection.competency().id(), selection.format(), sheetVersion);
        return count == null ? 0 : count;
    }

    /** A dedupe hit on an already finished batch: attach one of its unused tasks right away. */
    private void attachFinishedBatch(long user, long taskId, long jobId) {
        var items = jdbc.queryForList("""
                select b.id from daily_career.task_bank_item b where b.generation_job_id=? and b.status='ACTIVE'
                  and not exists (select 1 from daily_career.daily_task t where t.user_id=? and t.bank_item_id=b.id) order by b.id limit 1
                """, Long.class, jobId, user);
        if (!items.isEmpty()) jdbc.update("update daily_career.daily_task set bank_item_id=?,status='READY',revision=revision+1 where id=? and status='PREPARING'",
                items.getFirst(), taskId);
        else jdbc.update("update daily_career.daily_task set status='FAILED',failure_code='BANK_EXHAUSTED',revision=revision+1 where id=? and status='PREPARING' and exists (select 1 from daily_career.ai_job where id=? and status<>'QUEUED' and status<>'RUNNING')",
                taskId, jobId);
    }

    // ---- Answering

    @Transactional
    public Task start(long user, long taskId) {
        Map<String, Object> task = lock(user, taskId);
        if ("READY".equals(task.get("status"))) {
            jdbc.update("update daily_career.daily_task set status='IN_PROGRESS',started_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?", taskId);
            ensureMainTurn(user, taskId);
        } else if (!"IN_PROGRESS".equals(task.get("status"))) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        return view(user, taskId);
    }

    @Transactional
    public Task saveDraft(long user, long taskId, int turnNo, AnswerRequest request) {
        Map<String, Object> task = lock(user, taskId);
        Map<String, Object> turn = turn(taskId, turnNo);
        if (turn.get("submitted_at") != null || !editable(task, turnNo)) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        jdbc.update("update daily_career.daily_task_turn set answer_text=?,sheet_opened=sheet_opened or ?,saved_at=clock_timestamp() where id=?",
                answer(request, true), Boolean.TRUE.equals(request.sheetOpened()), turn.get("id"));
        return view(user, taskId);
    }

    @Transactional
    public Task submit(long user, long taskId, int turnNo, AnswerRequest request) {
        Map<String, Object> task = lock(user, taskId);
        if ("READY".equals(task.get("status")) && turnNo == 1) { start(user, taskId); task = lock(user, taskId); }
        Map<String, Object> turn = turn(taskId, turnNo);
        if (turn.get("submitted_at") != null || !editable(task, turnNo)) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        String answer = answer(request, false);
        jdbc.update("update daily_career.daily_task_turn set answer_text=?,sheet_opened=sheet_opened or ?,saved_at=clock_timestamp(),submitted_at=clock_timestamp() where id=?",
                answer, Boolean.TRUE.equals(request.sheetOpened()), turn.get("id"));
        long turnId = ((Number) turn.get("id")).longValue();
        if (turnNo == 1) {
            jdbc.update("update daily_career.daily_task set submitted_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?", taskId);
            if ("AI".equals(task.get("grading_mode")) && queueGrade(user, task, turnId)) setStatus(taskId, "GRADING", null);
            else fallbackToSelf(taskId, "AI".equals(task.get("grading_mode")) ? "AI_UNAVAILABLE" : null);
        } else if ("AI".equals(task.get("grading_mode")) && queueFollowup(user, task, turnId, turnNo)) {
            setStatus(taskId, "GRADING", null);
        } else {
            revealSelfFollowup(user, task, turnId, turnNo);
        }
        return view(user, taskId);
    }

    private boolean queueGrade(long user, Map<String, Object> task, long turnId) {
        long taskId = ((Number) task.get("id")).longValue();
        ObjectNode input = json.createObjectNode().put("taskId", taskId).put("turnId", turnId);
        try {
            ai.enqueue(user, GRADE_JOB, LoopPrompts.VERSION, input, "grade:" + taskId,
                    AiJobService.estimateTokens(prompts.overhead(LoopPrompts.Role.GRADING), gradingContext(task), turnText(turnId)),
                    LoopPrompts.Role.GRADING.maxOutputTokens);
            return true;
        } catch (ApiException unavailable) { return false; }
    }

    private boolean queueFollowup(long user, Map<String, Object> task, long turnId, int turnNo) {
        long taskId = ((Number) task.get("id")).longValue();
        ObjectNode input = json.createObjectNode().put("taskId", taskId).put("turnId", turnId);
        try {
            ai.enqueue(user, FOLLOWUP_JOB, LoopPrompts.VERSION, input, "followup:" + taskId + ":" + turnNo,
                    AiJobService.estimateTokens(prompts.overhead(LoopPrompts.Role.FOLLOWUP_TURN), gradingContext(task), conversationText(taskId)),
                    LoopPrompts.Role.FOLLOWUP_TURN.maxOutputTokens);
            return true;
        } catch (ApiException unavailable) { return false; }
    }

    /** AI grading unavailable: reveal the rubric for self-grading (design §7.2). */
    void fallbackToSelf(long taskId, String reason) {
        jdbc.update("update daily_career.daily_task set grading_mode='SELF',status='FEEDBACK',failure_code=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",
                reason, taskId);
    }

    @Transactional
    public Task selfGrade(long user, long taskId, SelfGradeRequest request) {
        Map<String, Object> task = lock(user, taskId);
        if (!"FEEDBACK".equals(task.get("status")) || !"SELF".equals(task.get("grading_mode")) || hasResult(taskId))
            throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        JsonNode rubric = bankItem(task).path("rubric");
        Set<String> met = new HashSet<>(request.metCriteria() == null ? List.of() : request.metCriteria());
        ArrayNode criteria = json.createArrayNode();
        int score = 0;
        for (JsonNode criterion : rubric) {
            String id = criterion.path("criterionId").asText();
            int points = criterion.path("points").asInt();
            boolean hit = met.remove(id);
            if (hit) score += points;
            criteria.add(json.createObjectNode().put("criterionId", id).put("description", criterion.path("description").asText())
                    .put("points", points).put("earnedPoints", hit ? points : 0).put("reason", hit ? "스스로 충족했다고 표시" : "스스로 미충족으로 표시"));
        }
        if (!met.isEmpty()) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        jdbc.update("""
                insert into daily_career.daily_task_result(task_id,user_id,status,base_score,final_score,criteria,feedback)
                values (?,?,'SELF_GRADED',?,?,?::jsonb,'루브릭으로 스스로 채점했어요.')
                """, taskId, user, score, score, criteria.toString());
        addReserveFollowup(user, task, 2);
        return view(user, taskId);
    }

    private void revealSelfFollowup(long user, Map<String, Object> task, long turnId, int turnNo) {
        jdbc.update("update daily_career.daily_task_turn set result_payload=?::jsonb where id=?",
                json.createObjectNode().put("feedback", "확인 포인트와 비교해 보세요.").put("selfChecked", true).toString(), turnId);
        if (!"SELF".equals(task.get("grading_mode"))) fallbackToSelf(((Number) task.get("id")).longValue(), "AI_UNAVAILABLE");
        addReserveFollowup(user, task, turnNo + 1);
    }

    /** AI-free follow-ups come from the stored reserve questions of the bank task. */
    private void addReserveFollowup(long user, Map<String, Object> task, int turnNo) {
        int limit = followupLimit(task.get("time_mode").toString());
        JsonNode reserve = reserveFollowups(task);
        int index = turnNo - 2;
        if (index >= limit || index >= reserve.size()) return;
        JsonNode followup = reserve.get(index);
        insertTurn(user, ((Number) task.get("id")).longValue(), turnNo, followup.path("question").asText(), followup.path("checkPoints"));
    }

    @Transactional
    public Task finish(long user, long taskId) {
        Map<String, Object> task = lock(user, taskId);
        if (!"FEEDBACK".equals(task.get("status"))) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        if (!hasResult(taskId)) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        jdbc.update("""
                update daily_career.daily_task set status='COMPLETED',completed_at=clock_timestamp(),completed_on=?,
                    updated_at=clock_timestamp(),revision=revision+1 where id=?
                """, LocalDate.now(SEOUL), taskId);
        progress.recompute(user, ((Number) task.get("competency_id")).longValue());
        return view(user, taskId);
    }

    @Transactional
    public Task saveCard(long user, long taskId, CardRequest request) {
        Map<String, Object> task = lock(user, taskId);
        if (!Set.of("FEEDBACK", "COMPLETED").contains(task.get("status").toString())) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        String title = bounded(request.title(), 200), body = bounded(request.body(), 4000);
        String line = bounded(request.interviewLine(), 500), prompt = bounded(request.reviewPrompt(), 500);
        jdbc.update("""
                insert into daily_career.answer_card(user_id,competency_id,task_id,title,body_markdown,interview_line,review_prompt,next_review_at)
                values (?,?,?,?,?,?,?,now()+interval '1 day')
                on conflict (task_id) do update set title=excluded.title,body_markdown=excluded.body_markdown,
                    interview_line=excluded.interview_line,review_prompt=excluded.review_prompt,updated_at=clock_timestamp(),
                    revision=daily_career.answer_card.revision+1
                """, user, task.get("competency_id"), taskId, title, body, line, prompt);
        return view(user, taskId);
    }

    @Transactional
    public Task feedback(long user, long taskId, FeedbackRequest request) {
        lock(user, taskId);
        if (request.usefulness() == null || request.usefulness() < 1 || request.usefulness() > 5) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        String note = request.note() == null ? "" : request.note().strip();
        if (note.length() > 1000) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        jdbc.update("""
                insert into daily_career.task_feedback(task_id,user_id,usefulness,note) values (?,?,?,?)
                on conflict (task_id) do update set usefulness=excluded.usefulness,note=excluded.note
                """, taskId, user, request.usefulness(), note);
        return view(user, taskId);
    }

    /** "이상해요" (design §6.4): the bank task stops being selected; the score may be excluded from the estimate. */
    @Transactional
    public Task report(long user, long taskId, ReportRequest request) {
        Map<String, Object> task = lock(user, taskId);
        if (!Set.of("MATERIAL", "FEEDBACK", "FOLLOWUP").contains(String.valueOf(request.target()))
                || !Set.of("FACT_ERROR", "GRADING_ERROR", "AMBIGUOUS", "OUTSIDE_SHEET", "OTHER").contains(String.valueOf(request.reason())))
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        String note = request.note() == null ? "" : request.note().strip();
        if (note.length() > 1000) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        boolean exclude = Boolean.TRUE.equals(request.excludeScore());
        jdbc.update("insert into daily_career.task_issue_report(user_id,task_id,bank_item_id,target,reason,note,exclude_score) values (?,?,?,?,?,?,?)",
                user, taskId, task.get("bank_item_id"), request.target(), request.reason(), note, exclude);
        if (task.get("bank_item_id") != null)
            jdbc.update("update daily_career.task_bank_item set status='SUSPENDED' where id=? and status='ACTIVE'", task.get("bank_item_id"));
        if (exclude) {
            jdbc.update("update daily_career.daily_task_result set score_excluded=true,updated_at=clock_timestamp() where task_id=?", taskId);
            if ("COMPLETED".equals(task.get("status"))) progress.recompute(user, ((Number) task.get("competency_id")).longValue());
        }
        return view(user, taskId);
    }

    // ---- Shared helpers used by the AI handlers

    void ensureMainTurn(long user, long taskId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from daily_career.daily_task_turn where task_id=? and turn_no=1)", Boolean.class, taskId);
        if (Boolean.TRUE.equals(exists)) return;
        Map<String, Object> task = jdbc.queryForMap("select * from daily_career.daily_task where id=?", taskId);
        insertTurn(user, taskId, 1, bankItem(task).path("task").path("question").asText(), json.createArrayNode());
    }

    void insertTurn(long user, long taskId, int turnNo, String question, JsonNode checkPoints) {
        jdbc.update("""
                insert into daily_career.daily_task_turn(task_id,user_id,turn_no,kind,question_payload,check_payload)
                values (?,?,?,?,?::jsonb,?::jsonb) on conflict (task_id,turn_no) do nothing
                """, taskId, user, turnNo, turnNo == 1 ? "MAIN" : "FOLLOWUP",
                json.createObjectNode().put("question", question).toString(), checkPoints.toString());
    }

    void setStatus(long taskId, String status, String failure) {
        jdbc.update("update daily_career.daily_task set status=?,failure_code=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",
                status, failure, taskId);
    }

    JsonNode bankItem(Map<String, Object> task) {
        Object id = task.get("bank_item_id");
        if (id == null) throw new ApiException(ApiErrorCode.TASK_STATE_CONFLICT);
        Map<String, Object> row = jdbc.queryForMap("select task_payload::text as task,rubric_payload::text as rubric from daily_career.task_bank_item where id=?", id);
        try {
            return json.createObjectNode().<ObjectNode>set("task", json.readTree(row.get("task").toString()))
                    .set("rubric", json.readTree(row.get("rubric").toString()));
        } catch (Exception invalid) { throw new IllegalStateException("Invalid bank item", invalid); }
    }

    JsonNode reserveFollowups(Map<String, Object> task) {
        try {
            return json.readTree(jdbc.queryForObject("select reserve_followups::text from daily_career.task_bank_item where id=?", String.class, task.get("bank_item_id")));
        } catch (Exception invalid) { throw new IllegalStateException("Invalid reserve follow-ups", invalid); }
    }

    String gradingContext(Map<String, Object> task) {
        CompetencyStore.Sheet sheet = sheetOf(task);
        return bankItem(task).toString() + sheet.document().gradingExcerpt();
    }

    CompetencyStore.Sheet sheetOf(Map<String, Object> task) {
        Long version = jdbc.queryForObject("select sheet_version_id from daily_career.task_bank_item where id=?", Long.class, task.get("bank_item_id"));
        return store.sheet(version);
    }

    String turnText(long turnId) {
        String text = jdbc.queryForObject("select answer_text from daily_career.daily_task_turn where id=?", String.class, turnId);
        return text == null ? "" : text;
    }

    String conversationText(long taskId) {
        return String.join("\n", jdbc.queryForList("""
                select coalesce(question_payload->>'question','') || '\n' || coalesce(answer_text,'')
                from daily_career.daily_task_turn where task_id=? order by turn_no
                """, String.class, taskId));
    }

    static int followupLimit(String timeMode) {
        return switch (timeMode) { case "SHORT" -> 1; case "DEEP" -> 3; default -> 2; };
    }

    Map<String, Object> lock(long user, long taskId) {
        users.current(user);
        var rows = jdbc.queryForList("select * from daily_career.daily_task where id=? and user_id=? for update", taskId, user);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Long openTaskId(long user) {
        var rows = jdbc.queryForList("select id from daily_career.daily_task where user_id=? and status = any(?) order by id desc limit 1",
                Long.class, user, (Object) OPEN.toArray(String[]::new));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> turn(long taskId, int turnNo) {
        var rows = jdbc.queryForList("select * from daily_career.daily_task_turn where task_id=? and turn_no=?", taskId, turnNo);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private boolean editable(Map<String, Object> task, int turnNo) {
        String status = task.get("status").toString();
        if (turnNo == 1) return "IN_PROGRESS".equals(status);
        Integer last = jdbc.queryForObject("select max(turn_no)::int from daily_career.daily_task_turn where task_id=?", Integer.class, task.get("id"));
        return "FEEDBACK".equals(status) && last != null && last == turnNo;
    }

    private boolean hasResult(long taskId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from daily_career.daily_task_result where task_id=?)", Boolean.class, taskId));
    }

    private static String answer(AnswerRequest request, boolean draft) {
        String value = request == null || request.answer() == null ? "" : request.answer();
        if (value.length() > ANSWER_LIMIT || (!draft && value.isBlank())) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        return value;
    }

    private static String bounded(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new ApiException(ApiErrorCode.VALIDATION_FAILED);
        return value.strip();
    }

    // ---- Views

    private TaskSummary summary(long taskId) {
        Map<String, Object> task = jdbc.queryForMap("select * from daily_career.daily_task where id=?", taskId);
        JsonNode material = task.get("bank_item_id") == null ? null : bankItem(task).path("task");
        CompetencyStore.Competency competency = store.competency(((Number) task.get("competency_id")).longValue());
        return new TaskSummary(taskId, task.get("status").toString(), task.get("format").toString(), ref(competency),
                task.get("plan_reason").toString(), material == null ? null : material.path("title").asText(),
                material == null ? null : material.path("estimatedMinutes").asInt(), (String) task.get("failure_code"));
    }

    @Transactional(readOnly = true)
    public Task view(long user, long taskId) {
        users.current(user);
        var rows = jdbc.queryForList("select * from daily_career.daily_task where id=? and user_id=?", taskId, user);
        if (rows.isEmpty()) throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        Map<String, Object> task = rows.getFirst();
        String status = task.get("status").toString();
        CompetencyStore.Competency competency = store.competency(((Number) task.get("competency_id")).longValue());
        JsonNode item = task.get("bank_item_id") == null ? null : bankItem(task);
        boolean revealed = task.get("submitted_at") != null && !"GRADING".equals(status);
        Material material = item == null ? null : new Material(item.path("task").path("title").asText(), item.path("task").path("situation").asText(),
                item.path("task").path("material").asText(), item.path("task").path("question").asText(), item.path("task").path("estimatedMinutes").asInt(),
                revealed ? strings(item.path("task").path("modelAnswerPoints")) : null);
        SheetRef sheet = null;
        if (item != null) {
            CompetencyStore.Sheet value = sheetOf(task);
            sheet = new SheetRef(value.versionNo(), value.reviewLabel(), value.document().body());
        }
        List<Turn> turns = turns(taskId);
        Map<String, Object> result = jdbc.queryForList("select * from daily_career.daily_task_result where task_id=?", taskId).stream().findFirst().orElse(null);
        List<Criterion> rubric = null;
        if (item != null && (revealed || result != null)) rubric = criteria(item, result);
        Result resultView = result == null ? null : new Result(result.get("status").toString(), number(result.get("base_score")),
                number(result.get("final_score")), (String) result.get("feedback"), strings(result.get("missing_points")),
                (String) result.get("improved_answer"), (String) result.get("hold_reason"), Boolean.TRUE.equals(result.get("score_excluded")));
        var cards = jdbc.queryForList("select title,body_markdown,interview_line,review_prompt from daily_career.answer_card where task_id=?", taskId);
        Card card = cards.isEmpty() ? null : new Card(cards.getFirst().get("title").toString(), cards.getFirst().get("body_markdown").toString(),
                cards.getFirst().get("interview_line").toString(), cards.getFirst().get("review_prompt").toString());
        var usefulness = jdbc.queryForList("select usefulness::int from daily_career.task_feedback where task_id=?", Integer.class, taskId);
        Boolean pending = jdbc.queryForObject("""
                select exists(select 1 from daily_career.ai_job where user_id=? and status in ('QUEUED','RUNNING')
                  and (input_snapshot->>'taskId')::bigint=?)
                """, Boolean.class, user, taskId);
        return new Task(taskId, status, task.get("format").toString(), task.get("time_mode").toString(), task.get("grading_mode").toString(),
                followupLimit(task.get("time_mode").toString()), ref(competency), task.get("plan_reason").toString(),
                (String) task.get("failure_code"), material, sheet, turns, rubric, resultView,
                result == null ? null : parse(result.get("card_draft")), card, usefulness.isEmpty() ? null : usefulness.getFirst(),
                Boolean.TRUE.equals(pending), ((Number) task.get("revision")).intValue());
    }

    private List<Turn> turns(long taskId) {
        var turns = new ArrayList<Turn>();
        for (Map<String, Object> row : jdbc.queryForList("select * from daily_career.daily_task_turn where task_id=? order by turn_no", taskId)) {
            boolean submitted = row.get("submitted_at") != null;
            JsonNode result = parse(row.get("result_payload"));
            int turnNo = ((Number) row.get("turn_no")).intValue();
            boolean followup = turnNo > 1;
            turns.add(new Turn(turnNo, row.get("kind").toString(), parse(row.get("question_payload")).path("question").asText(),
                    (String) row.get("answer_text"), submitted, submitted && result == null,
                    result == null ? null : result.path("feedback").asText(null),
                    followup && submitted && result != null ? strings(row.get("check_payload")) : null,
                    result == null ? null : strings(result.path("checkPointsMet")), number(row.get("score_delta"))));
        }
        return turns;
    }

    private List<Criterion> criteria(JsonNode item, Map<String, Object> result) {
        JsonNode graded = result == null ? null : parse(result.get("criteria"));
        var values = new ArrayList<Criterion>();
        for (JsonNode criterion : item.path("rubric")) {
            String id = criterion.path("criterionId").asText();
            JsonNode score = graded == null ? null : TaskValidation.find(graded, id);
            values.add(new Criterion(id, criterion.path("description").asText(), criterion.path("points").asInt(),
                    score == null ? null : score.path("earnedPoints").asInt(), score == null ? null : score.path("reason").asText()));
        }
        return values;
    }

    private static CompetencyRef ref(CompetencyStore.Competency competency) {
        return new CompetencyRef(competency.code(), competency.title(), competency.areaTitle(), competency.priority());
    }

    private JsonNode parse(Object value) {
        if (value == null) return null;
        try { return json.readTree(value.toString()); } catch (Exception invalid) { throw new IllegalStateException(invalid); }
    }

    private List<String> strings(Object value) {
        JsonNode node = value instanceof JsonNode existing ? existing : parse(value);
        if (node == null || !node.isArray()) return List.of();
        var values = new ArrayList<String>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static Integer number(Object value) { return value == null ? null : ((Number) value).intValue(); }
}
