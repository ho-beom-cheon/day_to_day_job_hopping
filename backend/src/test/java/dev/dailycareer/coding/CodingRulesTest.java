package dev.dailycareer.coding;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodingRulesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

    @Test void missesRestartAtThreeDaysAndCleanRetriesStretchToFourteenThenEnd() {
        assertThat(CodingRules.schedule("SOLUTION_VIEWED", 0, DAY)).isEqualTo(new CodingRules.Schedule(1, DAY.plusDays(3)));
        assertThat(CodingRules.schedule("SUCCESS", 1, DAY)).isEqualTo(new CodingRules.Schedule(2, DAY.plusDays(7)));
        assertThat(CodingRules.schedule("SUCCESS", 2, DAY)).isEqualTo(new CodingRules.Schedule(3, DAY.plusDays(14)));
        assertThat(CodingRules.schedule("SUCCESS", 3, DAY)).isEqualTo(CodingRules.Schedule.NONE);
        assertThat(CodingRules.schedule("FAILED", 3, DAY)).isEqualTo(new CodingRules.Schedule(1, DAY.plusDays(3)));
        assertThat(CodingRules.schedule("SUCCESS", 0, DAY)).isEqualTo(CodingRules.Schedule.NONE);
        assertThat(CodingRules.manualRetry(DAY)).isEqualTo(new CodingRules.Schedule(2, DAY.plusDays(7)));
    }

    @Test void levelMovesOnlyAfterTwoSameOutcomesInARow() {
        assertThat(CodingRules.targetLevel(null, List.of(), 1)).isEqualTo(1);
        assertThat(CodingRules.targetLevel(2, List.of("SUCCESS", "SUCCESS"), 1)).isEqualTo(3);
        assertThat(CodingRules.targetLevel(2, List.of("FAILED", "SOLUTION_VIEWED"), 1)).isEqualTo(1);
        assertThat(CodingRules.targetLevel(2, List.of("SUCCESS", "FAILED"), 1)).isEqualTo(2);
        assertThat(CodingRules.targetLevel(5, List.of("SUCCESS", "SUCCESS"), 1)).isEqualTo(5);
    }

    @Test void weakRecentCategoriesComeFirstThenUntriedThenTheRest() {
        var order = CodingRules.categoryOrder(List.of(new CodingRules.Stat("DP", 4, 1), new CodingRules.Stat("STRING", 3, 2),
                new CodingRules.Stat("SORT", 2, 2)), Set.of("DP", "STRING", "SORT", "GREEDY"));
        assertThat(order.subList(0, 3)).containsExactly(new CodingRules.Ordered("DP", "WEAK_CATEGORY"), new CodingRules.Ordered("STRING", "WEAK_CATEGORY"),
                new CodingRules.Ordered("DATA_STRUCTURE", "NEW_CATEGORY"));
        assertThat(order).contains(new CodingRules.Ordered("SORT", "NEXT"), new CodingRules.Ordered("GREEDY", "NEXT")).hasSize(CodingRules.CATEGORIES.size());
    }

    @Test void picksOnePerCategoryClosestToTheTargetLevelBeforeRepeating() {
        var order = List.of(new CodingRules.Ordered("DP", "WEAK_CATEGORY"), new CodingRules.Ordered("SORT", "NEW_CATEGORY"));
        var candidates = List.of(new CodingRules.Candidate(1, "DP", 2), new CodingRules.Candidate(2, "DP", 3), new CodingRules.Candidate(3, "DP", 4),
                new CodingRules.Candidate(4, "SORT", 1));
        assertThat(CodingRules.pick(3, order, candidates, Map.of("DP", 3, "SORT", 1)))
                .containsExactly(new CodingRules.Pick(2, "WEAK_CATEGORY"), new CodingRules.Pick(4, "NEW_CATEGORY"), new CodingRules.Pick(1, "WEAK_CATEGORY"));
        assertThat(CodingRules.pick(10, order, candidates, Map.of())).hasSize(4);
    }

    @Test void problemKeyPrefersTheLinkThenTheUrl() {
        assertThat(CodingRules.problemKey(7L, "https://x", "t")).isEqualTo("link:7");
        assertThat(CodingRules.problemKey(null, " https://School.programmers.co.kr/A ", "t")).isEqualTo("url:https://school.programmers.co.kr/a");
        assertThat(CodingRules.problemKey(null, null, " Two Sum ")).isEqualTo("title:two sum");
    }
}
