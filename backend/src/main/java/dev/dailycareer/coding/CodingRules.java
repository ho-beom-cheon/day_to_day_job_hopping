package dev.dailycareer.coding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure rules of the coding test track (design §4.7.3, §4.7.5): retry schedule, level targets and recommendation order. */
final class CodingRules {
    private CodingRules() {}

    static final List<String> CATEGORIES = List.of("DATA_STRUCTURE", "SORT", "BRUTE_FORCE", "GRAPH_SEARCH", "BINARY_SEARCH", "TWO_POINTER",
            "GREEDY", "DP", "STRING", "IMPLEMENTATION", "OTHER", "SQL_SELECT", "SQL_AGGREGATE", "SQL_JOIN", "SQL_SUBQUERY", "SQL_WINDOW", "SQL_FUNCTION");
    static final Set<String> RESULTS = Set.of("SUCCESS", "FAILED", "TIMEOUT", "SOLUTION_VIEWED");

    record Schedule(int step, LocalDate nextRetryAt) {
        static final Schedule NONE = new Schedule(0, null);
    }

    /** Gaps by step: 1 → 3 days, 2 → 7 days, 3 → 14 days. Step 0 means no retry is planned. */
    static Schedule schedule(String result, int previousStep, LocalDate solvedOn) {
        if (!"SUCCESS".equals(result)) return at(1, solvedOn);
        return switch (previousStep) {
            case 1 -> at(2, solvedOn);
            case 2 -> at(3, solvedOn);
            default -> Schedule.NONE;
        };
    }

    /** "다시 풀기 추가" on a problem solved without help: retry in 7 days. */
    static Schedule manualRetry(LocalDate today) { return at(2, today); }

    static Schedule at(int step, LocalDate from) {
        int days = switch (step) { case 1 -> 3; case 2 -> 7; case 3 -> 14; default -> throw new IllegalArgumentException("step"); };
        return new Schedule(step, from.plusDays(days));
    }

    /**
     * Level to aim for in a category: the level of the latest success, one up after two clean successes in a row,
     * one down after two misses in a row. {@code recentResults} is newest first.
     */
    static int targetLevel(Integer lastSuccessLevel, List<String> recentResults, int lowestAvailable) {
        int base = lastSuccessLevel == null ? lowestAvailable : lastSuccessLevel;
        if (recentResults.size() >= 2) {
            boolean up = recentResults.get(0).equals("SUCCESS") && recentResults.get(1).equals("SUCCESS");
            boolean down = !recentResults.get(0).equals("SUCCESS") && !recentResults.get(1).equals("SUCCESS");
            if (up) base++;
            else if (down) base--;
        }
        return Math.max(0, Math.min(5, base));
    }

    record Stat(String category, int attempts, int successes) {
        double rate() { return attempts == 0 ? 1 : (double) successes / attempts; }
    }

    /** Below this recent success rate a category counts as weak and is recommended before untried ones. */
    static final double WEAK_RATE = 0.7;

    /**
     * Category order for new problems: weak categories (recent success rate under 70%, weakest first), then categories
     * never tried, then the rest. {@code everTried} holds categories with any log at all.
     */
    static List<Ordered> categoryOrder(List<Stat> recent, Set<String> everTried) {
        List<Ordered> order = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        recent.stream().filter(stat -> stat.attempts() > 0 && stat.rate() < WEAK_RATE)
                .sorted(Comparator.comparingDouble(Stat::rate).thenComparing(Stat::attempts, Comparator.reverseOrder())
                        .thenComparing(stat -> CATEGORIES.indexOf(stat.category())))
                .forEach(stat -> { if (seen.add(stat.category())) order.add(new Ordered(stat.category(), "WEAK_CATEGORY")); });
        for (String category : CATEGORIES)
            if (!everTried.contains(category) && seen.add(category)) order.add(new Ordered(category, "NEW_CATEGORY"));
        for (String category : CATEGORIES)
            if (seen.add(category)) order.add(new Ordered(category, "NEXT"));
        return order;
    }

    record Ordered(String category, String reason) {}

    record Candidate(long linkId, String category, Integer level) {}

    /**
     * Fills {@code slots} with one problem per category in order, repeating passes while problems remain.
     * Within a category the level closest to its target wins, lower level and older link first on ties.
     */
    static List<Pick> pick(int slots, List<Ordered> order, List<Candidate> unsolved, Map<String, Integer> targets) {
        List<Pick> picks = new ArrayList<>();
        List<Candidate> left = new ArrayList<>(unsolved);
        boolean progress = true;
        while (picks.size() < slots && progress) {
            progress = false;
            for (Ordered ordered : order) {
                if (picks.size() >= slots) break;
                int target = targets.getOrDefault(ordered.category(), 1);
                var best = left.stream().filter(candidate -> candidate.category().equals(ordered.category()))
                        .min(Comparator.comparingInt((Candidate candidate) -> Math.abs(level(candidate) - target))
                                .thenComparingInt(CodingRules::level).thenComparingLong(Candidate::linkId));
                if (best.isEmpty()) continue;
                left.remove(best.get());
                picks.add(new Pick(best.get().linkId(), ordered.reason()));
                progress = true;
            }
        }
        return picks;
    }

    record Pick(long linkId, String reason) {}

    private static int level(Candidate candidate) { return candidate.level() == null ? 1 : candidate.level(); }

    /** Same-problem key: a recommended link, otherwise the URL, otherwise the title. */
    static String problemKey(Long linkId, String url, String title) {
        if (linkId != null) return "link:" + linkId;
        if (url != null && !url.isBlank()) return "url:" + url.strip().toLowerCase(java.util.Locale.ROOT);
        return "title:" + title.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
