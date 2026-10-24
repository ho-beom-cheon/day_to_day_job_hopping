-- Full reset before the competency daily loop (docs/competency-daily-loop-design-20261024.md §11.1).
-- Accounts, settings, integration connections and AI cost history stay; every legacy learning
-- object is removed. Recovery uses the legacy-before-reset git tag and the pre-reset database backup.

drop view daily_career.study_source_readiness;

alter table daily_career.ai_job drop constraint fk_ai_job_03;
drop index daily_career.ix_fk_ai_job_068;
alter table daily_career.ai_job drop column conversation_id;

drop table
    daily_career.sync_attempt,
    daily_career.sync_job,
    daily_career.integration_mapping,
    daily_career.notion_sync_item,
    daily_career.notion_sync_batch,
    daily_career.notion_target,
    daily_career.notion_target_set,
    daily_career.ai_message,
    daily_career.ai_conversation,
    daily_career.ai_practice_question_report,
    daily_career.ai_practice_wrong_note,
    daily_career.ai_practice_run_answer,
    daily_career.ai_practice_run,
    daily_career.ai_practice_attempt,
    daily_career.ai_learning_topic,
    daily_career.extra_learning_time,
    daily_career.extra_learning,
    daily_career.extra_learning_catalog,
    daily_career.wrong_answer_occurrence,
    daily_career.wrong_answer,
    daily_career.review_task,
    daily_career.study_evaluation_part,
    daily_career.study_evaluation,
    daily_career.monthly_evaluation,
    daily_career.grading_result,
    daily_career.grading_run,
    daily_career.test_attempt_item,
    daily_career.test_attempt,
    daily_career.test_item,
    daily_career.test_version,
    daily_career.test,
    daily_career.problem_report,
    daily_career.problem_attempt,
    daily_career.problem_version,
    daily_career.problem,
    daily_career.study_completion,
    daily_career.schedule_preview,
    daily_career.schedule_change,
    daily_career.schedule_rest_weekday,
    daily_career.schedule_policy,
    daily_career.learning_session,
    daily_career.assigned_session_detail,
    daily_career.assigned_content,
    daily_career.assigned_learning_day,
    daily_career.learning_day_item,
    daily_career.learning_day,
    daily_career.user_curriculum,
    daily_career.study_topic_detail,
    daily_career.study_source_review,
    daily_career.learning_content_problem,
    daily_career.learning_content_detail,
    daily_career.learning_content_version,
    daily_career.learning_content,
    daily_career.subject,
    daily_career.catalog_import,
    daily_career.curriculum_item_content,
    daily_career.curriculum_unit_detail,
    daily_career.curriculum_unit,
    daily_career.curriculum_release,
    daily_career.curriculum_version,
    daily_career.curriculum;

drop function daily_career.guard_published_version();
drop function daily_career.guard_published_child();
drop function daily_career.record_study_completion();

-- Notifications, deliveries, outbox events and idempotency replies only described legacy learning.
delete from daily_career.notification_delivery;
delete from daily_career.notification;
delete from daily_career.outbox_event;
delete from daily_career.idempotency_record;

-- Keep AI cost history, but drop legacy learning content captured in job snapshots.
update daily_career.ai_job set input_snapshot='{}'::jsonb, result_payload=null;
