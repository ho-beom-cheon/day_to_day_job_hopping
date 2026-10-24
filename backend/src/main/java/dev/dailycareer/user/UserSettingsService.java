package dev.dailycareer.user;

import dev.dailycareer.common.api.ApiException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.common.api.ApiErrorCode.PRECONDITION_FAILED;
import static dev.dailycareer.common.api.ApiErrorCode.VALIDATION_FAILED;
import static dev.dailycareer.user.SettingsViews.*;

@Service
public class UserSettingsService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private static final Set<String> WEEKDAYS=Set.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY");
    private static final Set<String> THEMES=Set.of("LIGHT","DARK","SYSTEM");
    private final JdbcTemplate jdbc; private final UserAccountService users;

    public UserSettingsService(JdbcTemplate jdbc,UserAccountService users){this.jdbc=jdbc;this.users=users;}

    @Transactional(readOnly=true)
    public Settings settings(long user){users.current(user);return view(row(user));}

    @Transactional
    public Settings update(long user,SettingsPatch patch,long expected){users.current(user);validate(patch);var current=row(user);long revision=((Number)current.get("revision")).longValue();if(revision!=expected)throw new ApiException(PRECONDITION_FAILED);
        StudyPolicy policy=patch.studyPolicy()==null?policy(current):patch.studyPolicy();String weekday=patch.weekdayStudyStartTime()==null?(String)current.get("weekday_study_start_time"):patch.weekdayStudyStartTime();String weekend=patch.weekendStudyStartTime()==null?(String)current.get("weekend_study_start_time"):patch.weekendStudyStartTime();String theme=patch.theme()==null?(String)current.get("theme"):patch.theme();
        int changed=jdbc.update("update daily_career.user_setting set daily_goal_minutes=?,rest_days_per_week=?,rest_weekdays=?::varchar(16)[],weekday_study_start_time=?,weekend_study_start_time=?,theme=?,revision=revision+1,updated_at=clock_timestamp() where user_id=? and revision=?",policy.dailyStudyMinutes(),policy.restDaysPerWeek(),arrayLiteral(policy.restWeekdays()),weekday,weekend,theme,user,expected);if(changed!=1)throw new ApiException(PRECONDITION_FAILED);return settings(user);}

    static void validate(SettingsPatch patch){if(patch==null||(patch.studyPolicy()==null&&patch.weekdayStudyStartTime()==null&&patch.weekendStudyStartTime()==null&&patch.theme()==null))throw new ApiException(VALIDATION_FAILED);if(patch.studyPolicy()!=null){var p=patch.studyPolicy();if(p.dailyStudyMinutes()==null||p.dailyStudyMinutes()<15||p.dailyStudyMinutes()>480||p.restDaysPerWeek()==null||p.restDaysPerWeek()<0||p.restDaysPerWeek()>6||p.restWeekdays()==null||p.restWeekdays().size()!=p.restDaysPerWeek()||new HashSet<>(p.restWeekdays()).size()!=p.restWeekdays().size()||!WEEKDAYS.containsAll(p.restWeekdays()))throw new ApiException(VALIDATION_FAILED);}if(patch.weekdayStudyStartTime()!=null&&!validTime(patch.weekdayStudyStartTime()))throw new ApiException(VALIDATION_FAILED);if(patch.weekendStudyStartTime()!=null&&!validTime(patch.weekendStudyStartTime()))throw new ApiException(VALIDATION_FAILED);if(patch.theme()!=null&&!THEMES.contains(patch.theme()))throw new ApiException(VALIDATION_FAILED);}

    private Map<String,Object> row(long user){return jdbc.queryForMap("select daily_goal_minutes,rest_days_per_week,array_to_string(rest_weekdays,',') rest_weekdays,weekday_study_start_time,weekend_study_start_time,theme,locale,timezone,revision,updated_at from daily_career.user_setting where user_id=?",user);}
    private Settings view(Map<String,Object> row){return new Settings(policy(row),(String)row.get("weekday_study_start_time"),(String)row.get("weekend_study_start_time"),(String)row.get("theme"),"KO",(String)row.get("timezone"),((Number)row.get("revision")).intValue(),time(row.get("updated_at")));}
    private static StudyPolicy policy(Map<String,Object> row){String raw=(String)row.get("rest_weekdays");List<String> days=raw==null||raw.isBlank()?List.of():Arrays.asList(raw.split(","));return new StudyPolicy(((Number)row.get("daily_goal_minutes")).intValue(),((Number)row.get("rest_days_per_week")).intValue(),days);}
    private static boolean validTime(String value){if(value==null||value.length()!=5)return false;try{LocalTime.parse(value);return true;}catch(Exception ignored){return false;}}
    private static String arrayLiteral(List<String> values){return "{"+String.join(",",values)+"}";}
    private static OffsetDateTime time(Object value){return value instanceof Timestamp timestamp?timestamp.toInstant().atZone(SEOUL).toOffsetDateTime():((OffsetDateTime)value).atZoneSameInstant(SEOUL).toOffsetDateTime();}
}
