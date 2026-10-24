package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cost math for gpt-6-luna at the 2026-10-24 official price (design §7.1). */
class AiCostServiceTest {
    private static final AiCostService.Price LUNA = new AiCostService.Price(1, new BigDecimal("0.10"), new BigDecimal("0.01"),
            new BigDecimal("0.50"), LocalDate.of(2026, 10, 24), "https://developers.openai.com/api/docs/pricing");

    @Test void reservesTheWorstCaseOfFullOutputAndNoCache() {
        // 5k input + 4k output cap: (5000×0.10 + 4000×0.50) / 1M = $0.0025 → 3.75원 at 1,500원.
        assertThat(AiCostService.reservation(LUNA, new BigDecimal("1500"), 5_000, 4_000)).isEqualByComparingTo("3.75");
    }

    @Test void chargesCachedInputAtTheCachedRateAndRecordsTheSnapshot() throws Exception {
        var service = new AiCostService(null, new ObjectMapper(), new AiSettings("openai", "test", "gpt-6-luna"));
        var cost = service.cost(LUNA, new BigDecimal("1500"), new AiModelClient.Tokens(10_000, 4_000, 2_000, 500));
        // (6000×0.10 + 4000×0.01 + 2000×0.50) / 1M = $0.00164
        assertThat(cost.usd()).isEqualByComparingTo("0.00164");
        assertThat(cost.krw()).isEqualByComparingTo("2.46");
        var snapshot = new ObjectMapper().readTree(cost.snapshot());
        assertThat(snapshot.path("priceVerifiedAt").asText()).isEqualTo("2026-10-24");
        assertThat(snapshot.path("reasoningCountedAsOutput").asBoolean()).isTrue();
    }

    @Test void monthlyLimitCoversHeavyUseManyTimesOver() {
        // A heavy month (design §7.1): 0.7M input + 0.34M output ≈ 360원, far below the 15,000원 hard limit.
        BigDecimal month = new BigDecimal("0.10").multiply(new BigDecimal("0.7")).add(new BigDecimal("0.50").multiply(new BigDecimal("0.34")))
                .multiply(new BigDecimal("1500"));
        assertThat(month).isEqualByComparingTo("360");
        assertThat(month.multiply(BigDecimal.TEN)).isLessThan(new BigDecimal("15000"));
    }
}
