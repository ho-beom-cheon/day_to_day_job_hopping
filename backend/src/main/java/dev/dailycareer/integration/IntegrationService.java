package dev.dailycareer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import dev.dailycareer.notification.push.WebPushSettings;
import dev.dailycareer.integration.slack.SlackSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.integration.IntegrationViews.*;

@Service
public class IntegrationService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private static final Set<String> DAYS=Set.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY");
    private static final Set<String> SLACK_EVENTS=Set.of("STUDY_REMINDER","DAILY_SUMMARY","WEEKLY_REPORT","MONTHLY_REPORT","INTEGRATION_FAILURE");
    private final JdbcTemplate jdbc; private final UserAccountService users; private final ObjectMapper json;
    private final WebPushSettings webPush; private final SlackSettings slackSettings;
    public IntegrationService(JdbcTemplate jdbc,UserAccountService users,ObjectMapper json,
            WebPushSettings webPush, SlackSettings slackSettings){this.jdbc=jdbc;this.users=users;this.json=json;this.webPush=webPush;this.slackSettings=slackSettings;}

    @Transactional(readOnly=true) public NotificationSettings settings(long user){users.current(user);return settingsRow(row(user));}
    @Transactional public NotificationSettings update(long user,NotificationSettingsPatch p,long expected){users.current(user);var current=row(user);long revision=((Number)current.get("notification_revision")).longValue();if(revision!=expected)throw new ApiException(PRECONDITION_FAILED);validate(p);var merged=(com.fasterxml.jackson.databind.node.ObjectNode)parse(current.get("notification_settings")).deepCopy();if(p.studyReminder()!=null)merged.set("studyReminder",json.valueToTree(p.studyReminder()));if(p.testReminder()!=null)merged.set("testReminder",json.valueToTree(p.testReminder()));if(p.streakReminder()!=null)merged.set("streakReminder",json.valueToTree(p.streakReminder()));if(p.weeklyReport()!=null)merged.set("weeklyReport",json.valueToTree(p.weeklyReport()));if(p.pushEnabled()!=null)merged.put("pushEnabled",p.pushEnabled());int n=jdbc.update("""
            update daily_career.user_setting set notification_settings=?::jsonb,
                scheduled_reminders_enabled=coalesce(?,scheduled_reminders_enabled),
                scheduled_reminders_enabled_at=case
                    when ? is null then scheduled_reminders_enabled_at
                    when ?=false then null
                    when scheduled_reminders_enabled then scheduled_reminders_enabled_at
                    else clock_timestamp() end,
                notification_revision=notification_revision+1,updated_at=clock_timestamp()
            where user_id=? and notification_revision=?
            """,merged.toString(),p.scheduledRemindersEnabled(),p.scheduledRemindersEnabled(),p.scheduledRemindersEnabled(),user,expected);if(n!=1)throw new ApiException(PRECONDITION_FAILED);
        if(Boolean.FALSE.equals(p.scheduledRemindersEnabled()))cancelScheduled(user);
        return settingsRow(row(user));}
    public PushConfig pushConfig(){return new PushConfig(webPush.enabled(),webPush.enabled()?webPush.publicKey():null);}
    @Transactional(readOnly=true) public NotionConnection notion(long user){users.current(user);var rows=jdbc.queryForList("select * from daily_career.integration_connection where user_id=? and provider='NOTION' order by id desc limit 1",user);if(rows.isEmpty())return new NotionConnection(false,"DISCONNECTED",null,null,"2026-03-11",null,false,0);var r=rows.getFirst();String status="ACTIVE".equals(r.get("status"))?(r.get("credential_ref")==null?"REAUTH_REQUIRED":"CONNECTED"):"DISCONNECTED";JsonNode c=parse(r.get("config"));return new NotionConnection("CONNECTED".equals(status),status,text(c,"workspaceName"),(String)r.get("external_workspace_id"),"2026-03-11",null,c.path("targetsConfigured").asBoolean(false),((Number)r.get("revision")).intValue());}
    @Transactional(readOnly=true) public SlackConnection slack(long user){users.current(user);boolean configured=slackSettings.enabled(user);var rows=jdbc.queryForList("select * from daily_career.integration_connection where user_id=? and provider='SLACK' order by id desc limit 1",user);if(rows.isEmpty())return new SlackConnection(configured,configured?slackSettings.channelName():null,false,List.of(),OffsetDateTime.now(SEOUL),0);var r=rows.getFirst();JsonNode c=parse(r.get("config"));List<String> events=new ArrayList<>();c.path("eventTypes").forEach(v->events.add(v.asText()));return new SlackConnection(configured,configured?c.path("channelName").asText(slackSettings.channelName()):null,configured&&"ACTIVE".equals(r.get("status"))&&c.path("enabled").asBoolean(false),events,time(r.get("updated_at")),((Number)r.get("revision")).intValue());}
    @Transactional public SlackConnection updateSlack(long user,SlackPatch p,long expected){users.current(user);if(p.enabled()==null&&p.eventTypes()==null)throw new ApiException(VALIDATION_FAILED);if(p.eventTypes()!=null&&(!SLACK_EVENTS.containsAll(p.eventTypes())||new HashSet<>(p.eventTypes()).size()!=p.eventTypes().size()))throw new ApiException(VALIDATION_FAILED);if(Boolean.TRUE.equals(p.enabled())&&!slackSettings.enabled(user))throw new ApiException(SLACK_NOT_CONFIGURED);var rows=jdbc.queryForList("select * from daily_career.integration_connection where user_id=? and provider='SLACK' and status='ACTIVE' order by id desc limit 1",user);if(rows.isEmpty()){if(expected!=0)throw new ApiException(PRECONDITION_FAILED);jdbc.update("insert into daily_career.integration_connection(user_id,provider,status,credential_ref,config,api_version) values (?,'SLACK','ACTIVE',?,?::jsonb,'webhook-v1')",user,nullable(slackSettings.credentialRef(user)),write(Map.of("enabled",Boolean.TRUE.equals(p.enabled()),"eventTypes",p.eventTypes()==null?List.of():p.eventTypes())));}else{var r=rows.getFirst();long revision=((Number)r.get("revision")).longValue();if(revision!=expected)throw new ApiException(PRECONDITION_FAILED);JsonNode c=parse(r.get("config")).deepCopy();if(p.enabled()!=null)((com.fasterxml.jackson.databind.node.ObjectNode)c).put("enabled",p.enabled());if(p.eventTypes()!=null)((com.fasterxml.jackson.databind.node.ObjectNode)c).set("eventTypes",json.valueToTree(p.eventTypes()));int n=jdbc.update("update daily_career.integration_connection set credential_ref=?,config=?::jsonb,revision=revision+1,updated_at=clock_timestamp() where id=? and user_id=? and revision=?",nullable(slackSettings.credentialRef(user)),c.toString(),r.get("id"),user,expected);if(n!=1)throw new ApiException(PRECONDITION_FAILED);if(Boolean.FALSE.equals(p.enabled()))jdbc.update("update daily_career.notification_delivery set status='CANCELED',last_error_code='SLACK_DISABLED',updated_at=clock_timestamp(),revision=revision+1 where integration_connection_id=? and user_id=? and channel='SLACK' and status='QUEUED'",r.get("id"),user);}return slack(user);}
    private Map<String,Object> row(long user){return jdbc.queryForMap("select notification_settings,notification_revision,scheduled_reminders_enabled,updated_at from daily_career.user_setting where user_id=?",user);}
    private NotificationSettings settingsRow(Map<String,Object> r){JsonNode n=parse(r.get("notification_settings"));return new NotificationSettings(timed(n,"studyReminder"),test(n),timed(n,"streakReminder"),weekly(n),n.path("pushEnabled").asBoolean(),(Boolean)r.get("scheduled_reminders_enabled"),time(r.get("updated_at")),((Number)r.get("notification_revision")).intValue());}
    static void validate(NotificationSettingsPatch p){if(p.studyReminder()==null&&p.testReminder()==null&&p.streakReminder()==null&&p.weeklyReport()==null&&p.pushEnabled()==null&&p.scheduledRemindersEnabled()==null)throw new ApiException(VALIDATION_FAILED);for(TimedReminder r:List.of(p.studyReminder()==null?new TimedReminder(false,"00:00"):p.studyReminder(),p.streakReminder()==null?new TimedReminder(false,"00:00"):p.streakReminder()))if(r.enabled()==null||!validTime(r.time()))throw new ApiException(VALIDATION_FAILED);if(p.testReminder()!=null&&(p.testReminder().enabled()==null||p.testReminder().beforeMinutes()==null||p.testReminder().beforeMinutes()<1||p.testReminder().beforeMinutes()>1440))throw new ApiException(VALIDATION_FAILED);if(p.weeklyReport()!=null&&(p.weeklyReport().enabled()==null||!DAYS.contains(p.weeklyReport().dayOfWeek())||!validTime(p.weeklyReport().time())))throw new ApiException(VALIDATION_FAILED);}
    private void cancelScheduled(long user){
        jdbc.update("""
            update daily_career.notification_delivery d set status='CANCELED',last_error_code='REMINDER_OPTED_OUT',
                updated_at=clock_timestamp(),revision=d.revision+1
            from daily_career.notification n
            where d.notification_id=n.id and d.user_id=? and d.status='QUEUED'
                and n.dedupe_key like 'SCHEDULED:%'
            """,user);
    }
    private static boolean validTime(String v){try{LocalTime.parse(v);return v.length()==5;}catch(Exception e){return false;}}
    private JsonNode parse(Object v){try{return json.readTree(v.toString());}catch(Exception e){throw new IllegalStateException("Invalid integration settings",e);}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private static TimedReminder timed(JsonNode n,String k){JsonNode v=n.path(k);return new TimedReminder(v.path("enabled").asBoolean(),v.path("time").asText());}private static TestReminder test(JsonNode n){JsonNode v=n.path("testReminder");return new TestReminder(v.path("enabled").asBoolean(),v.path("beforeMinutes").asInt());}private static WeeklyReminder weekly(JsonNode n){JsonNode v=n.path("weeklyReport");return new WeeklyReminder(v.path("enabled").asBoolean(),v.path("dayOfWeek").asText(),v.path("time").asText());}
    private static String text(JsonNode n,String k){return n.path(k).isTextual()?n.path(k).asText():null;}private static String nullable(String v){return v==null||v.isBlank()?null:v;}private static OffsetDateTime time(Object v){return v instanceof Timestamp t?t.toInstant().atZone(SEOUL).toOffsetDateTime():((OffsetDateTime)v).atZoneSameInstant(SEOUL).toOffsetDateTime();}
}
