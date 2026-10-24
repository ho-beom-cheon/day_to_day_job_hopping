package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** The packaged map, sheets and calibration files must stay publishable (design §3, §3.2, §6.3). */
class CompetencyContentTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void mapHasThirtyNineCompetenciesWithSeventeenPriorityOne() throws Exception {
        JsonNode map = json.readTree(new ClassPathResource("competency/v1/map.json").getInputStream());
        var codes = new HashSet<String>();
        int priorityOne = 0;
        for (JsonNode item : map.path("competencies")) {
            assertThat(codes.add(item.path("code").asText())).isTrue();
            if ("P1".equals(item.path("priority").asText())) priorityOne++;
        }
        assertThat(codes).hasSize(39);
        assertThat(priorityOne).isEqualTo(17);
    }

    @Test void everySheetParsesCitesSourcesAndHasAValidCalibrationSet() throws Exception {
        JsonNode map = json.readTree(new ClassPathResource("competency/v1/map.json").getInputStream());
        var codes = new HashSet<String>();
        map.path("competencies").forEach(item -> codes.add(item.path("code").asText()));
        Resource[] sheets = new PathMatchingResourcePatternResolver().getResources("classpath:competency/v1/sheets/*.md");
        assertThat(sheets).hasSize(5);
        for (Resource resource : sheets) {
            SheetDocument sheet = SheetDocument.parse(resource.getContentAsString(StandardCharsets.UTF_8));
            assertThat(codes).contains(sheet.code());
            assertThat(sheet.review()).contains("사용자 미검수");
            assertThat(sheet.sources()).allSatisfy(source -> assertThat(source.url()).startsWith("https://"));
            assertThat(sheet.gradingExcerpt()).contains("[K1]");
            JsonNode calibration = json.readTree(new ClassPathResource("competency/v1/calibration/" + sheet.code() + ".json").getInputStream());
            assertThat(calibration.path("code").asText()).isEqualTo(sheet.code());
            TaskValidation.rubric(calibration.path("rubric"), sheet.criteriaIds());
            var labels = new HashSet<String>();
            int previousMax = -1;
            for (JsonNode answer : calibration.path("answers")) {
                labels.add(answer.path("label").asText());
                assertThat(answer.path("text").asText()).isNotBlank();
                assertThat(answer.path("expectedMin").asInt()).isLessThan(answer.path("expectedMax").asInt()).isGreaterThan(previousMax - 5);
                previousMax = answer.path("expectedMax").asInt();
            }
            assertThat(labels).containsExactlyInAnyOrder("WEAK", "MEDIUM", "STRONG");
        }
    }
}
