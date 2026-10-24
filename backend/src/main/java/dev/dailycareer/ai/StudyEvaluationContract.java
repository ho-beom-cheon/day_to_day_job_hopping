package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import java.util.*;

final class StudyEvaluationContract {
    private StudyEvaluationContract() {}
    static boolean purpose(String purpose) {
        return Set.of("STUDY_DAILY","STUDY_WEEKLY","STUDY_MONTHLY","STUDY_FINAL","STUDY_RETAKE","STUDY_GRADING").contains(purpose);
    }
    static JsonNode validate(ObjectMapper json,String purpose,JsonNode snapshot,JsonNode candidate) {
        if("STUDY_GRADING".equals(purpose))return StudyAssessment.grade(json,snapshot.path("assessment"),answers(snapshot.path("answers")),candidate);
        var input=snapshot.path("providerInput");
        return StudyAssessment.validate(json,json.convertValue(input.path("plan"),StudyPolicy.Plan.class),
                Arrays.asList(json.convertValue(input.path("sources"),StudyAssessment.Source[].class)),candidate,
                StudyAssessment.strings(snapshot.path("previousPrompts")));
    }
    static Map<String,String> answers(JsonNode node) {
        var result=new LinkedHashMap<String,String>();
        StudyAssessment.require(node.isObject(),"INVALID_ANSWERS");
        node.fields().forEachRemaining(e->{StudyAssessment.require(e.getValue().isTextual(),"INVALID_ANSWER");result.put(e.getKey(),e.getValue().asText());});
        return result;
    }
}
