package dev.dailycareer.dashboard;

import dev.dailycareer.exam.ExamService;
import dev.dailycareer.exam.ExamViews;
import dev.dailycareer.learning.LearningQueryService;
import dev.dailycareer.learning.LearningViews;
import dev.dailycareer.progress.ProgressService;
import dev.dailycareer.progress.ProgressViews;
import dev.dailycareer.user.UserAccountService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.dashboard.DashboardViews.*;

@Service
public class DashboardService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc; private final UserAccountService users; private final LearningQueryService learning;
    private final ProgressService progress; private final ExamService exams;
    public DashboardService(JdbcTemplate jdbc,UserAccountService users,LearningQueryService learning,ProgressService progress,ExamService exams){this.jdbc=jdbc;this.users=users;this.learning=learning;this.progress=progress;this.exams=exams;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Dashboard dashboard(long user) {
        var account=users.current(user);
        LearningViews.Curriculum curriculum=learning.current(user);
        if(curriculum==null)return new Dashboard(account,null,null,null,null,0,null,new CharacterState("CAREER_GUIDE","NEUTRAL",false),true,java.util.List.of());
        long course=curriculum.curriculumId();
        LocalDate today=learning.today();
        LearningViews.LearningDay learningDay=learning.day(user,course,today);
        ProgressViews.Overall overall=progress.overall(user,course);
        LocalDate monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ProgressViews.PeriodProgress weekly=progress.periodProgress(user,course,monday,monday.plusDays(6));
        Integer unresolved=jdbc.queryForObject("select count(*)::int from daily_career.wrong_answer where user_id=? and status<>'MASTERED'",Integer.class,user);
        ExamViews.Exam next=exams.exams(user,null,null,0,100).content().stream().filter(exam->exam.activeAttemptId()!=null||!exam.availableUntil().isBefore(java.time.OffsetDateTime.now(SEOUL))).findFirst().orElse(null);
        var recent=jdbc.query("""
                select i.id,s.title,s.category,i.status,d.local_date
                from daily_career.learning_day_item i
                join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id and s.user_id=i.user_id
                join daily_career.learning_day d on d.id=i.learning_day_id and d.user_id=i.user_id
                where i.user_id=? and i.user_curriculum_id=? and i.activity_type<>'TEST'
                  and i.status in ('IN_PROGRESS','COMPLETED')
                order by coalesce(i.completed_at,i.started_at,i.updated_at) desc,i.id desc limit 4
                """,(rs,n)->new RecentSession(rs.getLong("id"),rs.getString("title"),rs.getString("category"),rs.getString("status"),rs.getDate("local_date").toLocalDate()),user,course);
        String mood="COMPLETED".equals(learningDay.status())?"CELEBRATE":"NONE".equals(learningDay.status())||"REST".equals(learningDay.status())?"NEUTRAL":"ENCOURAGING";
        return new Dashboard(account,curriculum,learningDay,overall,weekly,unresolved==null?0:unresolved,next,new CharacterState("CAREER_GUIDE",mood,false),false,recent);
    }
}
