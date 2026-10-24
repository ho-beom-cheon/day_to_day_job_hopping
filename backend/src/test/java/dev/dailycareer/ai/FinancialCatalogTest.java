package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FinancialCatalogTest {
    @Test void everyPublishedSourceSupportsAllOfferedDurationsAndKeepsAnswerAlignment() throws Exception {
        try(var stream=getClass().getResourceAsStream("/db/migration/V29__financial_backend_topic_catalog.sql")) {
            assertThat(stream).isNotNull();
            String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            var match=Pattern.compile("jsonb_array_elements\\('(.+)'::jsonb\\) LOOP",Pattern.DOTALL).matcher(sql);
            assertThat(match.find()).isTrue();
            JsonNode lessons=new ObjectMapper().readTree(match.group(1).replace("''","'"));
            assertThat(lessons.size()).isEqualTo(98);
            Map<String,Integer> counts=new HashMap<>();
            Set<String> slugs=new HashSet<>();
            for(JsonNode lesson:lessons) {
                assertThat(slugs.add(lesson.path("slug").asText())).isTrue();
                counts.merge(lesson.path("subject").asText(),1,Integer::sum);
                for(int minutes:List.of(30,60,90,120))
                    assertThat(AiPracticePlan.availableDifficulties(minutes,lesson.path("body").asText())).containsExactly(3,4,5);
                var q=lesson.path("questions").get(0);
                assertThat(q.path("choices").get(q.path("answer").asInt()-1).asText()).isEqualTo(lesson.path("keyPoints").get(0).asText());
                assertThat(lesson.path("body").asText()).contains("## 실습", "## 참고 자료");
                assertThat(lesson.path("sources").get(0).path("url").asText()).startsWith("https://");
            }
            assertThat(counts).hasSize(7);
            assertThat(counts.values()).allMatch(n->n==14);
        }
    }
}
