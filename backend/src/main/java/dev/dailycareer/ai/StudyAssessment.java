package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Fail closed at the paid-output boundary. Shape alone does not establish correctness. */
public final class StudyAssessment {
    public record Source(String id,String version,String body,List<String> objectiveIds,
                         boolean reviewed,boolean published,String executionEvidence) {}
    private StudyAssessment() {}
    public static void validateSources(List<Source> sources, StudyPolicy.Plan plan) {
        StudyPolicy.requireComplete(plan);
        var covered=new HashSet<String>();var ids=new HashSet<String>();
        require(!sources.isEmpty(),"SOURCE_MISSING");
        for(Source s:sources) {
            require(s.id()!=null && !s.id().isBlank() && ids.add(s.id()) && s.version()!=null && !s.version().isBlank()
                    && s.reviewed() && s.published() && s.body()!=null && s.body().length()>=200
                    && s.objectiveIds()!=null && !s.objectiveIds().isEmpty(),"SOURCE_NOT_READY");
            covered.addAll(s.objectiveIds());
        }
        for(var slot:plan.slots())require(covered.containsAll(slot.objectiveIds()),"OBJECTIVE_SOURCE_MISSING");
    }
    public static ObjectNode validate(ObjectMapper json, StudyPolicy.Plan plan,List<Source> sources,
                                      JsonNode candidate,Collection<String> previousPrompts) {
        validateSources(sources,plan);
        require(candidate.isObject() && "READY".equals(candidate.path("status").asText())
                && candidate.path("failureReason").asText().isBlank(),"GENERATION_FAILED");
        candidate=candidate.deepCopy();
        JsonNode questions=candidate.path("questions");
        require(questions.isArray() && questions.size()==plan.slots().size(),"QUESTION_COUNT_MISMATCH");
        var sourceById=new HashMap<String,Source>();sources.forEach(s->sourceById.put(s.id(),s));
        var prompts=new ArrayList<>(previousPrompts);
        for(int i=0;i<questions.size();i++) {
            var q=questions.get(i);var slot=plan.slots().get(i);
            require(q.path("id").asText().equals(slot.id()) && q.path("type").asText().equals(slot.type().name())
                    && q.path("difficulty").isInt() && q.path("difficulty").intValue()==slot.difficulty()
                    && q.path("seconds").isInt() && q.path("seconds").intValue()==slot.seconds()
                    && strings(q.path("objectiveIds")).equals(slot.objectiveIds()),"SLOT_MISMATCH");
            String prompt=text(q,"prompt",30,6000);text(q,"explanation",20,6000);
            require(prompts.stream().noneMatch(old->AiPracticeQuestions.similar(old,prompt)),"DUPLICATE_QUESTION");prompts.add(prompt);
            require(q.path("maxPoints").isInt() && q.path("maxPoints").intValue()==10,"INVALID_POINTS");
            var choices=strings(q.path("choices"));var wrong=strings(q.path("wrongAnswerExplanations"));
            require(q.path("answer").isInt() && q.path("criteria").isArray(),"INVALID_ANSWER");
            if(slot.type()==StudyPolicy.Type.MCQ) {
                require(choices.size()==4 && new HashSet<>(choices).size()==4 && choices.stream().noneMatch(String::isBlank)
                        && q.path("answer").intValue()>=1 && q.path("answer").intValue()<=4
                        && wrong.size()==3 && wrong.stream().noneMatch(String::isBlank) && q.path("criteria").isEmpty(),"INVALID_MCQ");
                // The selected choice is the canonical MCQ answer, independent of redundant model prose.
                ((ObjectNode)q).put("modelAnswer",choices.get(q.path("answer").intValue()-1));
            } else {
                text(q,"modelAnswer",1,6000);
                require(choices.isEmpty() && wrong.isEmpty() && q.path("answer").intValue()==0,"INVALID_WRITTEN_ANSWER");
                var ids=new HashSet<String>();int points=0;
                for(var c:q.path("criteria")) {
                    require(ids.add(text(c,"id",1,80)),"DUPLICATE_CRITERION");text(c,"description",5,1500);
                    require(c.path("points").isInt() && c.path("points").intValue()>0 && c.path("points").intValue()<=10,"INVALID_CRITERION_POINTS");
                    require(!strings(c.path("acceptableAlternatives")).isEmpty(),"MISSING_ACCEPTABLE_ALTERNATIVES");points+=c.path("points").intValue();
                }
                require(points==10,"RUBRIC_TOTAL_MISMATCH");
            }
            var covered=new HashSet<String>();require(q.path("sourceEvidence").isArray(),"SOURCE_EVIDENCE_MISSING");
            for(var e:q.path("sourceEvidence")) {
                Source s=sourceById.get(e.path("sourceId").asText());String objective=e.path("objectiveId").asText();
                String excerpt=text(e,"excerpt",8,2000);
                require(s!=null && s.version().equals(e.path("version").asText()) && s.body().contains(excerpt)
                        && s.objectiveIds().contains(objective) && slot.objectiveIds().contains(objective),"INVALID_SOURCE_EVIDENCE");covered.add(objective);
            }
            require(covered.containsAll(slot.objectiveIds()),"OBJECTIVE_EVIDENCE_MISSING");
        }
        ObjectNode result=candidate.deepCopy();result.put("promptVersion",StudyPrompts.VERSION);
        result.set("plan",json.valueToTree(plan));return result;
    }
    public static ObjectNode publicQuestions(ObjectMapper json,JsonNode assessment) {
        ObjectNode safe=json.createObjectNode();safe.set("plan",assessment.path("plan").deepCopy());
        ArrayNode questions=safe.putArray("questions");
        for(var q:assessment.path("questions")) {
            ObjectNode shown=questions.addObject();
            for(String key:List.of("id","type","objectiveIds","difficulty","seconds","prompt","choices"))shown.set(key,q.path(key).deepCopy());
        }
        return safe;
    }
    public static ObjectNode grade(ObjectMapper json,JsonNode assessment,Map<String,String> answers,JsonNode generated) {
        require(assessment.path("questions").isArray() && !assessment.path("questions").isEmpty(),"QUESTIONS_MISSING");
        var candidate=new HashMap<String,JsonNode>();
        require(generated.path("results").isArray(),"GRADING_RESULTS_MISSING");
        for(var r:generated.path("results"))require(candidate.put(r.path("questionId").asText(),r)==null,"DUPLICATE_GRADE");
        ObjectNode result=json.createObjectNode();ArrayNode rows=result.putArray("results");
        int total=0,earned=0,held=0;var known=new HashSet<String>();
        for(var q:assessment.path("questions")) {
            String id=q.path("id").asText();known.add(id);total+=10;
            ObjectNode row=rows.addObject().put("questionId",id);String answer=answers.getOrDefault(id,"");
            row.put("answer",answer);row.set("objectiveIds",q.path("objectiveIds").deepCopy());
            row.set("correctAnswer",q.path("modelAnswer").deepCopy());row.set("explanation",q.path("explanation").deepCopy());
            if("MCQ".equals(q.path("type").asText())) {
                require(answer.isEmpty() || answer.matches("[1-4]"),"INVALID_CHOICE");
                int points=answer.equals(q.path("answer").asText())?10:0;earned+=points;
                row.put("status","GRADED").put("earnedPoints",points).put("maxPoints",10);continue;
            }
            JsonNode r=candidate.remove(id);
            if(answer.isBlank()) {
                row.put("status","GRADED").put("earnedPoints",0).put("maxPoints",10).put("feedback","제출한 답안이 없습니다.");continue;
            }
            if(r==null || "HOLD".equals(r.path("status").asText())) {
                held++;row.put("status","HOLD").putNull("earnedPoints");
                row.put("holdReason",r==null?"채점 결과가 없어 보류했습니다.":text(r,"holdReason",1,2000));continue;
            }
            require("GRADED".equals(r.path("status").asText()) && r.path("criteria").isArray(),"INVALID_GRADING_STATUS");
            var limits=new HashMap<String,Integer>();q.path("criteria").forEach(c->limits.put(c.path("id").asText(),c.path("points").intValue()));
            int points=0;
            for(var c:r.path("criteria")) {
                Integer max=limits.remove(c.path("criterionId").asText());
                require(max!=null && c.path("earnedPoints").isInt() && c.path("earnedPoints").intValue()>=0
                        && c.path("earnedPoints").intValue()<=max,"INVALID_GRADED_POINTS");
                text(c,"reason",1,2000);points+=c.path("earnedPoints").intValue();
            }
            require(limits.isEmpty(),"MISSING_CRITERION_GRADE");
            row.put("status","GRADED").put("earnedPoints",points).put("maxPoints",10);
            row.put("feedback",text(r,"feedback",1,6000));row.put("improvedAnswer",text(r,"improvedAnswer",1,6000));
            strings(r.path("missingPoints"));row.set("missingPoints",r.path("missingPoints").deepCopy());
            row.set("criteria",r.path("criteria").deepCopy());earned+=points;
        }
        require(known.containsAll(answers.keySet()) && candidate.isEmpty(),"UNKNOWN_QUESTION_GRADE");
        result.put("status",held>0?"HOLD":"GRADED").put("heldQuestionCount",held);
        if(held>0)result.putNull("score");else result.put("score",total==0?0:Math.round(earned*10000.0/total)/100.0);
        return result;
    }
    static List<String> strings(JsonNode n) {
        require(n.isArray(),"EXPECTED_ARRAY");var out=new ArrayList<String>();
        for(JsonNode s:n){require(s.isTextual(),"EXPECTED_TEXT");out.add(s.textValue());}return out;
    }
    static String text(JsonNode n,String key,int min,int max) {
        JsonNode s=n.path(key);require(s.isTextual() && s.textValue().strip().length()>=min && s.textValue().length()<=max,"INVALID_"+key);
        return s.textValue();
    }
    static void require(boolean valid,String code){if(!valid)throw new IllegalArgumentException(code);}
}
