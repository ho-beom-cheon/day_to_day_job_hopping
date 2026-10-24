package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Typed preparation boundary for the new engine. No raw learner-history JSON is accepted. */
public final class StudyRequest {
    private StudyRequest() {}
    public static ObjectNode assessment(ObjectMapper json,StudyPolicy.Plan plan,List<StudyPolicy.Goal> goals,
                                         List<StudyAssessment.Source> sources,UUID variationSeed) {
        StudyAssessment.validateSources(sources,plan);
        var expected=new LinkedHashSet<String>();plan.slots().forEach(s->expected.addAll(s.objectiveIds()));
        var selected=goals.stream().filter(g->expected.contains(g.id())).toList();
        StudyAssessment.require(selected.stream().map(StudyPolicy.Goal::id).collect(java.util.stream.Collectors.toSet()).equals(expected),"OBJECTIVE_METADATA_MISSING");
        ObjectNode input=json.createObjectNode().put("promptVersion",StudyPrompts.VERSION).put("variationSeed",Objects.requireNonNull(variationSeed).toString());
        input.put("curriculumContext","6개월 목표, 26 교육 주차. 금융권 백엔드 면접 중심의 혼합 학습. 실제 달력 이수 기간은 달라질 수 있다.");
        input.set("careerContext",StudyPrompts.curriculum(json).path("careerContext"));
        input.set("plan",json.valueToTree(plan));input.set("objectives",json.valueToTree(selected));
        input.set("sources",json.valueToTree(sources));return input;
    }
    public static ObjectNode grading(ObjectMapper json,JsonNode assessment,Map<String,String> submittedAnswers) {
        ObjectNode input=json.createObjectNode().put("promptVersion",StudyPrompts.VERSION);
        var rows=input.putArray("questions");var ids=new HashSet<String>();
        for(var q:assessment.path("questions")) {
            String id=q.path("id").asText();ids.add(id);
            String answer=submittedAnswers.getOrDefault(id,"");
            StudyAssessment.require(answer!=null && answer.length()<=12000,"INVALID_ANSWER_LENGTH");
            if("MCQ".equals(q.path("type").asText()) || answer.isBlank())continue;
            var row=rows.addObject().put("answer",answer);
            for(String key:List.of("id","type","prompt","objectiveIds","modelAnswer","criteria","maxPoints","sourceEvidence"))row.set(key,q.path(key).deepCopy());
        }
        StudyAssessment.require(ids.containsAll(submittedAnswers.keySet()),"UNKNOWN_ANSWER");return input;
    }
}
