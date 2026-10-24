package dev.dailycareer.ai;

import org.springframework.jdbc.core.JdbcTemplate;

final class AiPracticeWrongRecorder {
    private AiPracticeWrongRecorder() {}

    static void record(JdbcTemplate jdbc, long user, long jobId, int questionNo) {
        jdbc.update("""
                insert into daily_career.ai_practice_wrong_note(user_id,ai_job_id,question_no)
                values (?,?,?)
                on conflict (user_id,ai_job_id,question_no) do update set
                    status='OPEN',wrong_count=ai_practice_wrong_note.wrong_count+1,
                    last_wrong_at=clock_timestamp(),updated_at=clock_timestamp()
                """, user, jobId, questionNo);
    }

    static void resolve(JdbcTemplate jdbc, long user, long jobId, int questionNo) {
        jdbc.update("""
                update daily_career.ai_practice_wrong_note set status='RESOLVED',updated_at=clock_timestamp()
                where user_id=? and ai_job_id=? and question_no=?
                """, user, jobId, questionNo);
    }
}
