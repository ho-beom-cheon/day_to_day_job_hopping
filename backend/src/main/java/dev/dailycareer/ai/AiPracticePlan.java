package dev.dailycareer.ai;

import java.util.ArrayList;
import java.util.List;

/** The study duration guides practice volume; solving time is separate. */
public record AiPracticePlan(int estimatedMinutes, int targetDifficulty, List<Integer> difficulties) {
    /** A short fixture cannot support a large set of distinct, source-grounded questions. */
    public boolean hasEnoughSource(String body) {
        return body != null && body.codePointCount(0, body.length()) >= Math.max(200, questionCount() * 45);
    }

    public static List<Integer> availableDifficulties(int estimatedMinutes, String body) {
        List<Integer> available = new ArrayList<>();
        for (int difficulty = 3; difficulty <= 5; difficulty++) {
            if (forMinutes(estimatedMinutes, difficulty).hasEnoughSource(body)) available.add(difficulty);
        }
        return List.copyOf(available);
    }
    static AiPracticePlan forMinutes(int estimatedMinutes) {
        return forMinutes(estimatedMinutes, 4);
    }

    public static AiPracticePlan forMinutes(int estimatedMinutes, int targetDifficulty) {
        if (estimatedMinutes < 1 || estimatedMinutes > 480)
            throw new IllegalArgumentException("Invalid planned session duration");
        if (targetDifficulty < 3 || targetDifficulty > 5)
            throw new IllegalArgumentException("Invalid practice difficulty");
        int base = Math.max(4, Math.min(18, (estimatedMinutes * 2 + 8) / 9));
        int count = switch (targetDifficulty) {
            case 3 -> Math.min(20, (base * 6 + 4) / 5);
            case 4 -> base;
            default -> Math.max(3, (base * 4 + 2) / 5);
        };
        List<Integer> levels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int level = switch (targetDifficulty) {
                case 3 -> index < (count * 3 + 3) / 4 ? 3 : 4;
                case 4 -> index < (count + 2) / 3 ? 3 : index < (count * 4 + 4) / 5 ? 4 : 5;
                default -> index < (count + 4) / 5 ? 4 : 5;
            };
            levels.add(level);
        }
        return new AiPracticePlan(estimatedMinutes, targetDifficulty, List.copyOf(levels));
    }

    public int questionCount() { return difficulties.size(); }
}
