package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static dev.dailycareer.ai.StudyPolicy.*;

class StudyAssessmentTest {
    private final ObjectMapper json=new ObjectMapper();
    private final String body="등록 요청이 중복될 때 저장소의 고유 제약과 재요청 식별자를 함께 확인한다. ".repeat(10);
    private Plan plan(Type type){return StudyPolicy.plan(Kind.DAILY,Mode.COMBINED,60,4,List.of(new Goal("g","SQL","중복 요청 검증",1,false)),List.of(type));}
    private List<StudyAssessment.Source> sources(){return List.of(new StudyAssessment.Source("s","v1",body,List.of("g"),true,true,""));}
    private ObjectNode candidate(Type type) {
        ObjectNode result=json.createObjectNode().put("status","READY").put("failureReason","");
        var q=result.putArray("questions").addObject().put("id","q1").put("type",type.name()).put("difficulty",4)
                .put("seconds",plan(type).slots().getFirst().seconds()).put("maxPoints",10)
                .put("prompt","동일한 신청 요청이 두 번 도착했고 현재 서버는 요청마다 새로운 행을 저장합니다. 어떤 근거로 수정안과 검증 방법을 고르겠습니까?")
                .put("modelAnswer","재요청 식별자와 고유 제약을 검토한다.").put("explanation","동일 요청을 식별하고 저장소의 제약과 재시도 응답을 함께 검증해야 합니다.");
        q.putArray("objectiveIds").add("g");
        if(type==Type.MCQ){q.putArray("choices").add("고유 제약과 요청 키").add("무조건 재시도").add("시간만 비교").add("모든 오류 무시");q.put("answer",1);q.putArray("wrongAnswerExplanations").add("중복 가능").add("식별 불가").add("원인 유실");q.putArray("criteria");}
        else {q.putArray("choices");q.put("answer",0);q.putArray("wrongAnswerExplanations");var c=q.putArray("criteria").addObject().put("id","c1").put("description","중복 식별과 저장소 제약의 역할 설명").put("points",10);c.putArray("acceptableAlternatives").add("동일한 의미의 요청 키 설명");}
        q.putArray("sourceEvidence").addObject().put("objectiveId","g").put("sourceId","s").put("version","v1").put("excerpt","저장소의 고유 제약과 재요청 식별자를 함께 확인한다.");
        return result;
    }
    @Test void acceptedQuestionHasEvidenceAndPublicProjectionHidesAllGradingData() {
        var valid=StudyAssessment.validate(json,plan(Type.MCQ),sources(),candidate(Type.MCQ),List.of());
        var shown=StudyAssessment.publicQuestions(json,valid).path("questions").get(0);
        assertThat(shown.has("answer")).isFalse();assertThat(shown.has("modelAnswer")).isFalse();
        assertThat(shown.has("explanation")).isFalse();assertThat(shown.has("criteria")).isFalse();
        assertThat(shown.has("sourceEvidence")).isFalse();
    }
    @Test void wrongVersionMissingGoalOrRepeatedQuestionCannotPass() {
        var bad=candidate(Type.MCQ);((ObjectNode)bad.path("questions").get(0).path("sourceEvidence").get(0)).put("version","v2");
        assertThatThrownBy(()->StudyAssessment.validate(json,plan(Type.MCQ),sources(),bad,List.of())).hasMessage("INVALID_SOURCE_EVIDENCE");
        var missing=candidate(Type.MCQ);((ObjectNode)missing.path("questions").get(0)).putArray("objectiveIds");
        assertThatThrownBy(()->StudyAssessment.validate(json,plan(Type.MCQ),sources(),missing,List.of())).hasMessage("SLOT_MISMATCH");
        var repeated=candidate(Type.MCQ);
        assertThatThrownBy(()->StudyAssessment.validate(json,plan(Type.MCQ),sources(),repeated,List.of(repeated.path("questions").get(0).path("prompt").asText()))).hasMessage("DUPLICATE_QUESTION");
    }
    @Test void multipleChoiceCanonicalAnswerComesFromValidatedChoiceEvenWhenModelProseIsEmpty() {
        var raw=candidate(Type.MCQ);((ObjectNode)raw.path("questions").get(0)).put("modelAnswer","");
        var valid=StudyAssessment.validate(json,plan(Type.MCQ),sources(),raw,List.of());
        assertThat(valid.path("questions").get(0).path("modelAnswer").asText()).isEqualTo("고유 제약과 요청 키");
        assertThat(raw.path("questions").get(0).path("modelAnswer").asText()).isEmpty();
    }
    @Test void unreviewedOrUnpublishedSourceNeverBecomesReady() {
        for(boolean reviewed:List.of(true,false)) {
            var source=List.of(new StudyAssessment.Source("s","v1",body,List.of("g"),reviewed,false,""));
            assertThatThrownBy(()->StudyAssessment.validateSources(source,plan(Type.MCQ))).hasMessage("SOURCE_NOT_READY");
        }
        var source=List.of(new StudyAssessment.Source("s","v1",body,List.of("g"),false,true,""));
        assertThatThrownBy(()->StudyAssessment.validateSources(source,plan(Type.MCQ))).hasMessage("SOURCE_NOT_READY");
    }
    @Test void multipleChoiceIsDeterministicAndDoesNotNeedModelGrades() {
        var results=json.createObjectNode();results.putArray("results");
        assertThat(StudyAssessment.grade(json,candidate(Type.MCQ),Map.of("q1","1"),results).path("score").asDouble()).isEqualTo(100);
        assertThat(StudyAssessment.grade(json,candidate(Type.MCQ),Map.of("q1","2"),results).path("score").asDouble()).isZero();
    }
    @Test void unansweredWrittenQuestionIsZeroWithoutSendingAnEmptyAnswerForPaidGrading() {
        var empty=json.createObjectNode();empty.putArray("results");
        assertThat(StudyAssessment.grade(json,candidate(Type.INTERVIEW),Map.of(),empty).path("score").asDouble()).isZero();
        assertThat(StudyRequest.grading(json,candidate(Type.INTERVIEW),Map.of()).path("questions")).isEmpty();
    }
    @Test void missingWrittenGradeIsHoldNotZeroAndPartialCreditIsNormalized() {
        var results=json.createObjectNode();results.putArray("results");
        var hold=StudyAssessment.grade(json,candidate(Type.INTERVIEW),Map.of("q1","답안"),results);
        assertThat(hold.path("status").asText()).isEqualTo("HOLD");assertThat(hold.path("score").isNull()).isTrue();
        var r=((ArrayNode)results.path("results")).addObject().put("questionId","q1").put("status","GRADED")
                .put("feedback","식별자는 설명했지만 제약 검증이 부족합니다.").put("improvedAnswer","식별자와 저장소 제약을 함께 검증합니다.").put("holdReason","");
        r.putArray("missingPoints").add("저장소 제약 검증");r.putArray("criteria").addObject().put("criterionId","c1").put("earnedPoints",6).put("reason","핵심의 일부를 설명했습니다.");
        assertThat(StudyAssessment.grade(json,candidate(Type.INTERVIEW),Map.of("q1","답안"),results).path("score").asDouble()).isEqualTo(60);
        ((ObjectNode)r.path("criteria").get(0)).put("earnedPoints",11);
        assertThatThrownBy(()->StudyAssessment.grade(json,candidate(Type.INTERVIEW),Map.of("q1","답안"),results)).hasMessage("INVALID_GRADED_POINTS");
    }
    @Test void allRolesAndSchemasLoadAndCurriculumHas130DistinctDays() {
        for(var role:StudyPrompts.Role.values())assertThat(StudyPrompts.instructions(role)).contains("금융권 백엔드");
        for(String schema:List.of("assessment","grading","learning","audit"))assertThat(StudyPrompts.schema(json,schema)).containsEntry("additionalProperties",false);
        var curriculum=StudyPrompts.curriculum(json);assertThat(curriculum.path("weeks").size()).isEqualTo(26);
        var days=new HashSet<Integer>();var objectives=new HashSet<String>();
        curriculum.path("weeks").forEach(w->w.path("days").forEach(d->{days.add(d.path("dayNo").intValue());d.path("objectives").forEach(g->assertThat(objectives.add(g.path("id").asText())).isTrue());}));
        assertThat(days).hasSize(130).contains(1,130);
    }
    @Test void providerProjectionNeverIncludesPrivateRecordsOrPriorQuestions() {
        var raw=json.createObjectNode().put("body",body).put("userId","private").put("priorPrompts","secret")
                .put("answers","secret").put("accuracy",0.4).put("contentVersionId","private");
        assertThat(StudyPrompts.practiceInput(json,raw).toString()).doesNotContain("private","secret","accuracy","priorPrompts").contains("body");
        raw.putArray("learningRecords").addObject().put("title","SQL 조인").put("status","COMPLETED").put("planned_date","2026-09-27");
        assertThat(StudyPrompts.feedbackInput(json,raw).toString()).contains("SQL 조인").doesNotContain("COMPLETED","planned_date","userId","2026-09-27");
    }
    @Test void typedPreparationRefusesMissingMetadataAndGradingIncludesOnlyCurrentWrittenAnswers() {
        assertThatThrownBy(()->StudyRequest.assessment(json,plan(Type.MCQ),List.of(),sources(),UUID.randomUUID())).hasMessage("OBJECTIVE_METADATA_MISSING");
        var input=StudyRequest.grading(json,candidate(Type.INTERVIEW),Map.of("q1","요청 키와 제약을 같이 검증합니다."));
        assertThat(input.path("questions").size()).isEqualTo(1);
        assertThat(input.toString()).doesNotContain("learningRecords","accuracy","priorPrompts");
        assertThat(StudyRequest.grading(json,candidate(Type.MCQ),Map.of("q1","1")).path("questions").isEmpty()).isTrue();
        assertThatThrownBy(()->StudyRequest.grading(json,candidate(Type.MCQ),Map.of("unknown","1"))).hasMessage("UNKNOWN_ANSWER");
    }
}
