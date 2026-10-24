package dev.dailycareer.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static dev.dailycareer.ai.StudyPolicy.*;

class StudyPolicyTest {
    private Goal goal(String id,int week,boolean extra){return new Goal(id,"SQL","조건과 근거를 설명한다",week,extra);}
    @Test void ratiosIncludeAnswerReviewAndDoNotGrowTheSelectedDuration() {
        for(int minutes:List.of(30,60,90,120))for(Kind kind:List.of(Kind.DAILY,Kind.WEEKLY,Kind.MONTHLY)) {
            Plan p=plan(kind,Mode.COMBINED,minutes,4,List.of(goal("g",1,false)),List.of(Type.MCQ));
            int share=kind==Kind.DAILY?30:kind==Kind.WEEKLY?50:70;
            assertThat(p.assessmentSeconds()).isEqualTo(minutes*60*share/100);
            assertThat(p.assessmentSeconds()+p.learningSeconds()).isEqualTo(minutes*60);
        }
    }
    @Test void monthlyReplacesWeeklyAndDailyOnEducationalBoundaries() {
        assertThat(due(1)).isEqualTo(Kind.DAILY);assertThat(due(5)).isEqualTo(Kind.WEEKLY);
        for(int day:List.of(20,40,65,85,105,130))assertThat(due(day)).isEqualTo(Kind.MONTHLY);
        assertThat(month(9)).isEqualTo(3);assertThat(month(13)).isEqualTo(3);
    }
    @Test void scopeIncludesExtraAndDeduplicatesIdentityWithoutAddingPreviousMonths() {
        var goals=List.of(goal("old",4,false),goal("regular",5,false),goal("extra",5,true),goal("regular",5,true),goal("future",9,false));
        assertThat(scope(goals,Kind.MONTHLY,8)).extracting(Goal::id).containsExactly("regular","extra");
        assertThat(scope(goals,Kind.WEEKLY,5)).hasSize(2);
        assertThat(scope(goals,Kind.FINAL,8)).extracting(Goal::id).containsExactly("old","regular","extra");
    }
    @Test void completedScopeCannotBeSilentlyTruncated() {
        var goals=new ArrayList<Goal>();for(int i=0;i<10;i++)goals.add(goal("g"+i,1,false));
        var p=plan(Kind.DAILY,Mode.COMBINED,30,4,goals,List.of(Type.INTERVIEW));
        assertThat(p.deferredObjectives()).hasSize(9);
        assertThatThrownBy(()->requireComplete(p)).hasMessage("ASSESSMENT_SCOPE_EXCEEDS_TIME");
        var f=plan(Kind.FINAL,Mode.COMBINED,30,4,goals,List.of(Type.INTERVIEW));
        assertThat(f.slots()).hasSize(10);assertThat(f.sessions()).hasSize(2);
        assertThat(f.totalSeconds()).isEqualTo(3000);assertThat(f.deferredObjectives()).isEmpty();
    }
    @Test void extraLearningHasNoDuplicateExamAndTimeDoesNotChangeDifficulty() {
        var g=List.of(goal("g",1,true));
        assertThat(plan(Kind.EXTRA,Mode.LEARN,60,3,g,List.of(Type.MCQ)).assessmentSeconds()).isZero();
        assertThat(plan(Kind.EXTRA,Mode.QUESTIONS,60,3,g,List.of(Type.MCQ)).learningSeconds()).isZero();
        assertThat(plan(Kind.EXTRA,Mode.COMBINED,120,3,g,List.of(Type.MCQ)).slots()).extracting(Slot::difficulty).containsOnly(3);
    }
}
