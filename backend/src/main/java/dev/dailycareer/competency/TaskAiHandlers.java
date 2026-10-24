package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.ai.AiJobHandler;
import dev.dailycareer.ai.AiRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AI job handlers of the daily loop. Inputs contain only the sheet, the task and this task's own answers (design §7). */
final class TaskAiHandlers {
    private TaskAiHandlers() {}

    @Component
    static class BankGenerate implements AiJobHandler {
        private final JdbcTemplate jdbc;
        private final ObjectMapper json;
        private final CompetencyStore store;
        private final LoopPrompts prompts;
        private final DailyTaskService tasks;

        BankGenerate(JdbcTemplate jdbc, ObjectMapper json, CompetencyStore store, LoopPrompts prompts, DailyTaskService tasks) {
            this.jdbc = jdbc;
            this.json = json;
            this.store = store;
            this.prompts = prompts;
            this.tasks = tasks;
        }

        @Override public String jobType() { return DailyTaskService.BANK_JOB; }

        @Override public AiRequest request(Job job) {
            JsonNode input = job.input();
            CompetencyStore.Sheet sheet = store.sheet(input.path("sheetVersionId").asLong());
            CompetencyStore.Competency competency = store.competency(input.path("competencyId").asLong());
            ObjectNode provider = json.createObjectNode();
            provider.putObject("competency").put("code", competency.code()).put("title", competency.title()).put("priority", competency.priority());
            provider.put("format", input.path("format").asText()).put("difficulty", input.path("difficulty").asInt())
                    .put("count", input.path("count").asInt()).put("sheet", sheet.document().body());
            provider.set("existingTitles", input.path("existingTitles"));
            if (input.hasNonNull("resumeClaimId")) provider.set("claimContext", claimContext(job.userId(), input.path("resumeClaimId").asLong()));
            return prompts.request(LoopPrompts.Role.TASK_GENERATE, provider);
        }

        private ObjectNode claimContext(long user, long claimId) {
            Map<String, Object> claim = jdbc.queryForMap("""
                    select c.claim_text,f.project_name,f.client_name,f.period_from,f.period_to,f.role_text,f.tech_text,f.confirmed_scope,f.caution_text,f.status
                    from daily_career.resume_claim c left join daily_career.career_fact f on f.id=c.career_fact_id and f.user_id=c.user_id
                    where c.id=? and c.user_id=?
                    """, claimId, user);
            ObjectNode context = json.createObjectNode().put("claim", claim.get("claim_text").toString()).put("pressureStyle", true);
            if ("CONFIRMED".equals(claim.get("status"))) context.set("fact", CareerFacts.factInput(json, claim));
            else context.putNull("fact");
            return context;
        }

        @Override public void complete(Job job, JsonNode result) {
            JsonNode input = job.input();
            long competency = input.path("competencyId").asLong(), sheetVersion = input.path("sheetVersionId").asLong();
            CompetencyStore.Sheet sheet = store.sheet(sheetVersion);
            var known = new HashSet<>(jdbc.queryForList("select fingerprint from daily_career.task_bank_item where competency_id=?", String.class, competency));
            List<TaskValidation.GeneratedTask> generated = TaskValidation.tasks(json, result, input.path("format").asText(), sheet.document().criteriaIds(), known);
            Long claim = input.hasNonNull("resumeClaimId") ? input.path("resumeClaimId").asLong() : null;
            for (TaskValidation.GeneratedTask task : generated) {
                jdbc.update("""
                        insert into daily_career.task_bank_item(competency_id,sheet_version_id,owner_user_id,resume_claim_id,format,difficulty,
                            task_payload,rubric_payload,reserve_followups,fingerprint,generation_job_id)
                        values (?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?)
                        """, competency, sheetVersion, claim == null ? null : job.userId(), claim, input.path("format").asText(),
                        input.path("difficulty").asInt(), task.task().toString(), task.rubric().toString(), task.reserveFollowups().toString(),
                        task.fingerprint(), job.id());
            }
            for (long taskId : jdbc.queryForList("select id from daily_career.daily_task where generation_job_id=? and status='PREPARING' order by id",
                    Long.class, job.id())) {
                long owner = jdbc.queryForObject("select user_id from daily_career.daily_task where id=?", Long.class, taskId);
                var items = jdbc.queryForList("""
                        select b.id from daily_career.task_bank_item b where b.generation_job_id=? and b.status='ACTIVE'
                          and not exists (select 1 from daily_career.daily_task t where t.user_id=? and t.bank_item_id=b.id) order by b.id limit 1
                        """, Long.class, job.id(), owner);
                if (items.isEmpty()) { tasks.setStatus(taskId, "FAILED", "BANK_EXHAUSTED"); continue; }
                jdbc.update("update daily_career.daily_task set bank_item_id=?,status='READY',failure_code=null,updated_at=clock_timestamp(),revision=revision+1 where id=?",
                        items.getFirst(), taskId);
                jdbc.update("""
                        insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,aggregate_id,aggregate_revision,payload,available_at)
                        values (?,?,'DAILY_TASK_READY','DAILY_TASK',?,0,'{}'::jsonb,now()) on conflict (event_key) do nothing
                        """, owner, "TASK_READY:" + taskId, taskId);
            }
        }

        @Override public void fail(Job job, String errorCode) {
            jdbc.update("update daily_career.daily_task set status='FAILED',failure_code=?,updated_at=clock_timestamp(),revision=revision+1 where generation_job_id=? and status='PREPARING'",
                    errorCode, job.id());
        }
    }

    @Component
    static class Grade implements AiJobHandler {
        private final JdbcTemplate jdbc;
        private final ObjectMapper json;
        private final LoopPrompts prompts;
        private final DailyTaskService tasks;

        Grade(JdbcTemplate jdbc, ObjectMapper json, LoopPrompts prompts, DailyTaskService tasks) {
            this.jdbc = jdbc;
            this.json = json;
            this.prompts = prompts;
            this.tasks = tasks;
        }

        @Override public String jobType() { return DailyTaskService.GRADE_JOB; }

        @Override public AiRequest request(Job job) {
            Map<String, Object> task = task(job);
            JsonNode item = tasks.bankItem(task);
            ObjectNode provider = json.createObjectNode();
            ObjectNode material = provider.putObject("task");
            for (String key : List.of("title", "situation", "material", "question")) material.put(key, item.path("task").path(key).asText());
            provider.set("rubric", item.path("rubric"));
            provider.put("sheetExcerpt", tasks.sheetOf(task).document().gradingExcerpt())
                    .put("answer", tasks.turnText(job.input().path("turnId").asLong()))
                    .put("followupAllowed", DailyTaskService.followupLimit(task.get("time_mode").toString()) > 0)
                    .put("pressureStyle", task.get("resume_claim_id") != null);
            return prompts.request(LoopPrompts.Role.GRADING, provider);
        }

        @Override public void complete(Job job, JsonNode result) {
            Map<String, Object> task = task(job);
            if (!"GRADING".equals(task.get("status"))) return;
            JsonNode item = tasks.bankItem(task);
            TaskValidation.Grading grading = TaskValidation.grading(json, result, item.path("rubric"));
            boolean hold = "HOLD".equals(grading.status());
            long taskId = job.input().path("taskId").asLong();
            jdbc.update("""
                    insert into daily_career.daily_task_result(task_id,user_id,status,base_score,final_score,criteria,feedback,missing_points,
                        improved_answer,hold_reason,card_draft)
                    values (?,?,?,?,?,?::jsonb,?,?::jsonb,?,?,?::jsonb)
                    """, taskId, job.userId(), grading.status(), hold ? null : grading.score(), hold ? null : grading.score(),
                    grading.criteria().toString(), result.path("feedback").asText(""), result.path("missingPoints").toString(),
                    result.path("improvedAnswer").asText(""), result.path("holdReason").asText(""), result.path("cardDraft").toString());
            jdbc.update("update daily_career.daily_task_turn set result_payload=?::jsonb where id=?",
                    json.createObjectNode().put("feedback", result.path("feedback").asText("")).toString(), job.input().path("turnId").asLong());
            String question = result.path("followupQuestion").asText("");
            if (!hold && !question.isBlank() && DailyTaskService.followupLimit(task.get("time_mode").toString()) > 0)
                tasks.insertTurn(job.userId(), taskId, 2, question, checkPoints(result.path("followupCheckPoints")));
            tasks.setStatus(taskId, "FEEDBACK", null);
        }

        @Override public void fail(Job job, String errorCode) {
            Map<String, Object> task = task(job);
            if ("GRADING".equals(task.get("status"))) tasks.fallbackToSelf(job.input().path("taskId").asLong(), errorCode);
        }

        private Map<String, Object> task(Job job) {
            return jdbc.queryForMap("select * from daily_career.daily_task where id=? and user_id=? for update", job.input().path("taskId").asLong(), job.userId());
        }

        private ArrayNode checkPoints(JsonNode value) {
            ArrayNode points = json.createArrayNode();
            if (value.isArray()) value.forEach(point -> { if (!point.asText("").isBlank()) points.add(point.asText()); });
            return points;
        }
    }

    @Component
    static class Followup implements AiJobHandler {
        private final JdbcTemplate jdbc;
        private final ObjectMapper json;
        private final LoopPrompts prompts;
        private final DailyTaskService tasks;

        Followup(JdbcTemplate jdbc, ObjectMapper json, LoopPrompts prompts, DailyTaskService tasks) {
            this.jdbc = jdbc;
            this.json = json;
            this.prompts = prompts;
            this.tasks = tasks;
        }

        @Override public String jobType() { return DailyTaskService.FOLLOWUP_JOB; }

        @Override public AiRequest request(Job job) {
            Map<String, Object> task = task(job);
            Map<String, Object> turn = turn(job);
            JsonNode item = tasks.bankItem(task);
            ObjectNode provider = json.createObjectNode();
            provider.putObject("task").put("title", item.path("task").path("title").asText()).put("question", item.path("task").path("question").asText());
            provider.set("rubric", item.path("rubric"));
            provider.put("sheetExcerpt", tasks.sheetOf(task).document().followupExcerpt());
            ArrayNode conversation = provider.putArray("conversation");
            for (Map<String, Object> row : jdbc.queryForList("select turn_no,question_payload::text as q,answer_text from daily_career.daily_task_turn where task_id=? and turn_no<? order by turn_no",
                    task.get("id"), turn.get("turn_no"))) {
                conversation.addObject().put("role", "interviewer").put("text", read(row.get("q")).path("question").asText());
                conversation.addObject().put("role", "learner").put("text", String.valueOf(row.get("answer_text")));
            }
            int turnNo = ((Number) turn.get("turn_no")).intValue();
            provider.put("currentQuestion", read(turn.get("question_payload")).path("question").asText());
            provider.set("currentCheckPoints", read(turn.get("check_payload")));
            provider.put("answer", String.valueOf(turn.get("answer_text")))
                    .put("remainingFollowups", Math.max(0, DailyTaskService.followupLimit(task.get("time_mode").toString()) - (turnNo - 1)))
                    .put("cumulativeDelta", cumulative(task.get("id"), turnNo))
                    .put("pressureStyle", task.get("resume_claim_id") != null);
            return prompts.request(LoopPrompts.Role.FOLLOWUP_TURN, provider);
        }

        @Override public void complete(Job job, JsonNode result) {
            Map<String, Object> task = task(job);
            if (!"GRADING".equals(task.get("status"))) return;
            Map<String, Object> turn = turn(job);
            int turnNo = ((Number) turn.get("turn_no")).intValue();
            if (!result.path("scoreDelta").isInt()) throw new IllegalArgumentException("Score delta missing");
            int cumulative = cumulative(task.get("id"), turnNo);
            int delta = TaskValidation.delta(result.path("scoreDelta").asInt(), cumulative);
            jdbc.update("update daily_career.daily_task_turn set result_payload=?::jsonb,score_delta=? where id=?",
                    json.createObjectNode().put("feedback", result.path("feedback").asText("")).<ObjectNode>set("checkPointsMet", result.path("checkPointsMet")).toString(),
                    delta, turn.get("id"));
            jdbc.update("""
                    update daily_career.daily_task_result set final_score=greatest(0,least(100,base_score+?)),card_draft=?::jsonb,updated_at=clock_timestamp()
                    where task_id=? and base_score is not null
                    """, cumulative + delta, result.path("cardDraft").toString(), task.get("id"));
            int remaining = DailyTaskService.followupLimit(task.get("time_mode").toString()) - (turnNo - 1);
            String next = result.path("nextQuestion").asText("");
            if (remaining > 0 && !next.isBlank())
                tasks.insertTurn(job.userId(), ((Number) task.get("id")).longValue(), turnNo + 1, next, result.path("nextCheckPoints"));
            tasks.setStatus(((Number) task.get("id")).longValue(), "FEEDBACK", null);
        }

        @Override public void fail(Job job, String errorCode) {
            Map<String, Object> task = task(job);
            if (!"GRADING".equals(task.get("status"))) return;
            jdbc.update("update daily_career.daily_task_turn set result_payload=?::jsonb where id=?",
                    json.createObjectNode().put("feedback", "AI 평가를 받지 못했어요. 확인 포인트와 직접 비교해 보세요.").put("selfChecked", true).toString(),
                    job.input().path("turnId").asLong());
            tasks.setStatus(((Number) task.get("id")).longValue(), "FEEDBACK", errorCode);
        }

        private int cumulative(Object taskId, int beforeTurn) {
            Integer value = jdbc.queryForObject("select coalesce(sum(score_delta),0)::int from daily_career.daily_task_turn where task_id=? and turn_no<?",
                    Integer.class, taskId, beforeTurn);
            return value == null ? 0 : value;
        }

        private Map<String, Object> task(Job job) {
            return jdbc.queryForMap("select * from daily_career.daily_task where id=? and user_id=? for update", job.input().path("taskId").asLong(), job.userId());
        }

        private Map<String, Object> turn(Job job) {
            return jdbc.queryForMap("select * from daily_career.daily_task_turn where id=?", job.input().path("turnId").asLong());
        }

        private JsonNode read(Object value) {
            try { return json.readTree(value.toString()); } catch (Exception invalid) { throw new IllegalStateException(invalid); }
        }
    }
}
