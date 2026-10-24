package dev.dailycareer.ai;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.dailycareer.ai.StudyPolicy.*;

class StudyEvaluationPlanTest {
    private Goal goal(int id,boolean extra){return new Goal("g"+id,"SQL","조건부 갱신과 결과 검증 "+id,1,extra);}
    @Test void additionalLearningIncreasesQuestionsAndNoScopeIsDroppedAtAnyDuration() {
        for(var kind:List.of(Kind.DAILY,Kind.WEEKLY,Kind.MONTHLY,Kind.FINAL))for(int minutes:List.of(30,60,90,120)) {
            var learned=new ArrayList<Goal>();for(int i=0;i<81;i++)learned.add(goal(i,i>2));learned.add(goal(0,true));
            var parts=StudyEvaluationPlan.split(kind,minutes,4,learned);
            assertThat(parts.stream().flatMap(p->p.slots().stream()).flatMap(s->s.objectiveIds().stream()).toList()).hasSize(81).doesNotHaveDuplicates();
            for(var part:parts){assertThat(part.slots().stream().mapToInt(Slot::seconds).sum()).isLessThanOrEqualTo(part.assessmentSeconds());assertThat(part.assessmentSeconds()+part.learningSeconds()).isEqualTo(part.totalSeconds());assertThat(part.slots()).hasSizeLessThanOrEqualTo(6);assertThat(part.deferredObjectives()).isEmpty();}
            assertThat(parts.stream().flatMap(p->p.slots().stream()).map(Slot::type).distinct()).containsExactly(Type.MCQ,Type.SHORT_ANSWER,Type.INTERVIEW);
        }
    }
    @Test void includesAllObjectivesEvenWhenInterviewQuestionDoesNotFitTheFirstPart() {
        var parts=StudyEvaluationPlan.split(Kind.DAILY,30,3,List.of(goal(1,false),goal(2,false),goal(3,true)));
        assertThat(parts).hasSize(2);assertThat(parts.getFirst().assessmentSeconds()).isEqualTo(540);
        assertThat(parts.getLast().slots().getFirst().type()).isEqualTo(Type.INTERVIEW);
        assertThat(parts.getLast().slots().getFirst().objectiveIds()).containsExactly("g3");
    }
}
