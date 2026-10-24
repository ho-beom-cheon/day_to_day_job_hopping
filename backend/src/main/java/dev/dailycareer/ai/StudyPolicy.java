package dev.dailycareer.ai;

import java.util.*;

/** Versioned rules for new study plans. Existing published assignments are immutable. */
public final class StudyPolicy {
    public static final String VERSION = "financial-backend-v1";
    public enum Kind { DAILY, WEEKLY, MONTHLY, FINAL, EXTRA }
    public enum Mode { LEARN, QUESTIONS, COMBINED }
    public enum Type { MCQ, SHORT_ANSWER, INTERVIEW }
    public record Goal(String id, String subject, String description, int week, boolean extra) {}
    public record Slot(String id, Type type, List<String> objectiveIds, int difficulty, int seconds) {}
    public record Plan(String policyVersion, Kind kind, Mode mode, int totalSeconds, int learningSeconds,
                       int assessmentSeconds, List<Slot> slots, List<List<String>> sessions,
                       List<String> deferredObjectives) {}
    private static final int[] MONTH_ENDS = {4,8,13,17,21,26};
    private StudyPolicy() {}

    public static int month(int week) {
        if (week < 1 || week > 26) throw new IllegalArgumentException("Week must be 1..26");
        for (int i=0;i<MONTH_ENDS.length;i++) if (week<=MONTH_ENDS[i]) return i+1;
        throw new IllegalStateException();
    }
    public static Kind due(int day) {
        if(day<1 || day>130)throw new IllegalArgumentException("Day must be 1..130");
        if(day%5!=0)return Kind.DAILY;
        return Arrays.stream(MONTH_ENDS).anyMatch(w->w==day/5)?Kind.MONTHLY:Kind.WEEKLY;
    }
    public static List<Goal> scope(Collection<Goal> learned, Kind kind, int week) {
        month(week);
        var unique = new LinkedHashMap<String,Goal>();
        for (Goal goal:learned) {
            if(goal.id()==null || goal.id().isBlank() || goal.description()==null || goal.description().isBlank()
                    || goal.subject()==null || goal.subject().isBlank())throw new IllegalArgumentException("Invalid objective");
            month(goal.week());
            boolean include=switch(kind) {
                case WEEKLY -> goal.week()==week;
                case MONTHLY -> month(goal.week())==month(week) && goal.week()<=week;
                case FINAL -> goal.week()<=week;
                default -> true; // Caller supplies exactly this completed session's objectives.
            };
            if(include) {
                Goal old=unique.putIfAbsent(goal.id(),goal);
                if(old!=null && (!old.description().equals(goal.description()) || !old.subject().equals(goal.subject())))
                    throw new IllegalArgumentException("Conflicting objective identity");
            }
        }
        return List.copyOf(unique.values());
    }

    /** seconds include answering AND explanation; final is partitioned, never truncated. */
    public static Plan plan(Kind kind, Mode mode, int minutes, int difficulty, List<Goal> goals, List<Type> types) {
        if(minutes<15 || minutes>480 || difficulty<3 || difficulty>5 || goals.isEmpty()
                || goals.size()>1000 || types.isEmpty() || new HashSet<>(types).size()!=types.size())
            throw new IllegalArgumentException("Invalid study plan");
        if(kind!=Kind.EXTRA && mode!=Mode.COMBINED)throw new IllegalArgumentException("Mode applies only to extra learning");
        goals=scope(goals,Kind.DAILY,1);
        int total=minutes*60;
        int share=kind==Kind.FINAL?100:kind==Kind.MONTHLY?70:kind==Kind.WEEKLY?50:30;
        if(kind==Kind.EXTRA)share=mode==Mode.LEARN?0:mode==Mode.QUESTIONS?100:30;
        int budget=total*share/100;
        if(budget==0)return new Plan(VERSION,kind,mode,total,total,0,List.of(),List.of(),List.of());
        var slots=new ArrayList<Slot>();var deferred=new ArrayList<String>();
        int used=0;
        for(int i=0;i<goals.size();i++) {
            Type type=types.get(i%types.size());
            int seconds=switch(type){case MCQ->90;case SHORT_ANSWER->180;case INTERVIEW->300;};
            if(kind!=Kind.FINAL && used+seconds>budget) {deferred.add(goals.get(i).id());continue;}
            slots.add(new Slot("q"+(slots.size()+1),type,List.of(goals.get(i).id()),difficulty,seconds));used+=seconds;
        }
        if(slots.isEmpty())throw new IllegalArgumentException("Insufficient assessment time");
        var sessions=new ArrayList<List<String>>();var current=new ArrayList<String>();int sessionSeconds=0;
        for(Slot s:slots) {
            if(sessionSeconds+s.seconds()>total && !current.isEmpty()) {sessions.add(List.copyOf(current));current.clear();sessionSeconds=0;}
            current.add(s.id());sessionSeconds+=s.seconds();
        }
        if(!current.isEmpty())sessions.add(List.copyOf(current));
        return new Plan(VERSION,kind,mode,kind==Kind.FINAL?used:total,kind==Kind.FINAL?0:total-budget,
                kind==Kind.FINAL?used:budget,List.copyOf(slots),List.copyOf(sessions),List.copyOf(deferred));
    }

    /** A completed scope cannot be silently shortened to fit. Split before teaching, or schedule more time. */
    public static void requireComplete(Plan plan) {
        if(!plan.deferredObjectives().isEmpty())throw new IllegalArgumentException("ASSESSMENT_SCOPE_EXCEEDS_TIME");
    }
}
