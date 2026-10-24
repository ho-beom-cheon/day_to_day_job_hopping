package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import dev.dailycareer.common.api.*;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** New policy preview is independent of existing learner assignments and never mutates them. */
@RestController
@RequestMapping("/api/v1/study-program")
public class StudyProgramController {
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    public StudyProgramController(ObjectMapper json,JdbcTemplate jdbc){this.json=json;this.jdbc=jdbc;}
    @GetMapping
    ResponseEntity<?> get(@AuthenticationPrincipal AppPrincipal user,HttpServletRequest request) {
        authenticated(user);
        var catalog=jdbc.queryForList("""
            select s.code as subject,count(*)::int as "publishedLessonCount"
            from daily_career.learning_content_version v
            join daily_career.learning_content c on c.id=v.learning_content_id
            join daily_career.subject s on s.id=c.subject_id
            where v.status='PUBLISHED' and c.enabled and c.owner_user_id is null
              and (exists(select 1 from daily_career.extra_learning_catalog e where e.content_version_id=v.id)
                or exists(select 1 from daily_career.curriculum_item_content ic
                  join daily_career.curriculum_unit u on u.id=ic.curriculum_unit_id
                  join daily_career.curriculum_version cv on cv.id=u.curriculum_version_id
                  join daily_career.curriculum cur on cur.id=cv.curriculum_id
                  where ic.content_version_id=v.id and cv.status='PUBLISHED' and cur.enabled
                    and cur.code<>'BACKEND-FOUNDATIONS-6M'))
            group by s.code order by s.code
            """);
        var topicPath=jdbc.queryForList("""
            select v.id::text as "contentVersionId",d.title,s.code as subject,t.stage,
                   t.recommended_week as week,d.learning_objective as objective
            from daily_career.study_topic_detail t
            join daily_career.learning_content_version v on v.id=t.content_version_id
            join daily_career.learning_content c on c.id=v.learning_content_id
            join daily_career.learning_content_detail d on d.content_version_id=v.id
            join daily_career.subject s on s.id=c.subject_id
            where v.status='PUBLISHED' and c.enabled and c.owner_user_id is null
            order by t.learning_order
            """);
        return ApiResponses.ok(request,Map.of("curriculum",StudyPrompts.curriculum(json),"availableCatalog",catalog,"topicPath",topicPath,
                "status","OUTLINE_READY","generationReady",false,
                "readinessMessage","아래 권장 흐름에서 실제 발행된 주제를 선택해 본문·실습과 AI 연습을 진행할 수 있습니다. 정규 일정 재배정과 서술형 통합 평가는 별도이며, 현재 진도는 유지됩니다.",
                "promptRoles",Arrays.stream(StudyPrompts.Role.values()).map(Enum::name).toList()));
    }
    @GetMapping("/preview")
    ResponseEntity<?> preview(@AuthenticationPrincipal AppPrincipal user,@RequestParam(defaultValue="1") int day,
                             @RequestParam(defaultValue="60") int minutes,@RequestParam(defaultValue="4") int difficulty,
                             @RequestParam(defaultValue="false") boolean finalAssessment,HttpServletRequest request) {
        authenticated(user);
        try {
            StudyPolicy.Kind kind=finalAssessment?StudyPolicy.Kind.FINAL:StudyPolicy.due(day);
            int week=(day-1)/5+1;var goals=new ArrayList<StudyPolicy.Goal>();
            for(JsonNode w:StudyPrompts.curriculum(json).path("weeks"))for(JsonNode d:w.path("days")) {
                if(d.path("dayNo").intValue()>day || (kind==StudyPolicy.Kind.DAILY && d.path("dayNo").intValue()!=day))continue;
                for(JsonNode goal:d.path("objectives"))goals.add(new StudyPolicy.Goal(goal.path("id").asText(),
                        goal.path("subject").asText(),goal.path("description").asText(),w.path("week").intValue(),false));
            }
            var scope=StudyPolicy.scope(goals,kind,week);
            var plan=StudyPolicy.plan(kind,StudyPolicy.Mode.COMBINED,minutes,difficulty,scope,List.of(StudyPolicy.Type.values()));
            return ApiResponses.ok(request,Map.of("plan",plan,"objectiveCount",scope.size(),"previewOnly",true,
                    "message",plan.deferredObjectives().isEmpty()?"목차 기준 예상입니다. 실제 평가는 완료한 정규·추가 목표로 편성합니다.":"범위가 시간보다 큽니다. 학습 전 목표 분할 또는 평가 시간 재편성이 필요합니다."));
        }catch(IllegalArgumentException e){throw new ApiException(ApiErrorCode.VALIDATION_FAILED);}
    }
    private static void authenticated(AppPrincipal user){if(user==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);}
}
