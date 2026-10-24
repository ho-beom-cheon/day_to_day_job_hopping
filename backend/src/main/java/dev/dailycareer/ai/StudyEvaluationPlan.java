package dev.dailycareer.ai;

import java.util.*;

/** Every learned objective receives a question; each part respects the session budget. */
public final class StudyEvaluationPlan {
    private StudyEvaluationPlan() {}
    public static List<StudyPolicy.Plan> split(StudyPolicy.Kind kind,int minutes,int difficulty,List<StudyPolicy.Goal> goals) {
        if(!List.of(30,60,90,120).contains(minutes) || difficulty<3 || difficulty>5 || goals.isEmpty())
            throw new IllegalArgumentException("Invalid evaluation plan");
        int budget=minutes*60*(kind==StudyPolicy.Kind.FINAL?100:kind==StudyPolicy.Kind.MONTHLY?70:kind==StudyPolicy.Kind.WEEKLY?50:30)/100;
        var unique=new LinkedHashMap<String,StudyPolicy.Goal>();
        goals.forEach(g->unique.putIfAbsent(g.id(),g));
        var result=new ArrayList<StudyPolicy.Plan>();var slots=new ArrayList<StudyPolicy.Slot>();int used=0,index=0;
        for(var goal:unique.values()) {
            var type=StudyPolicy.Type.values()[index%3];int seconds=switch(type){case MCQ->90;case SHORT_ANSWER->180;case INTERVIEW->300;};
            if(!slots.isEmpty() && (used+seconds>budget || slots.size()==6)) {result.add(part(kind,minutes*60,budget,slots));slots.clear();used=0;}
            slots.add(new StudyPolicy.Slot("q"+(++index),type,List.of(goal.id()),difficulty,seconds));used+=seconds;
        }
        if(!slots.isEmpty())result.add(part(kind,minutes*60,budget,slots));
        return List.copyOf(result);
    }
    private static StudyPolicy.Plan part(StudyPolicy.Kind kind,int total,int budget,List<StudyPolicy.Slot> slots) {
        return new StudyPolicy.Plan(StudyPolicy.VERSION,kind,StudyPolicy.Mode.COMBINED,total,total-budget,budget,
                List.copyOf(slots),List.of(slots.stream().map(StudyPolicy.Slot::id).toList()),List.of());
    }
}
