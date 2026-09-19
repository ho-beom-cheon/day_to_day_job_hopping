package dev.dailycareer.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonIdTest {
    private final ObjectMapper mapper = new ObjectMapper();
    record Sample(@JsonId Long id, long revision, double adherenceRate) {}

    @Test void preservesBigintPrecisionWithoutChangingNumbers() throws Exception {
        var value = new Sample(Long.MAX_VALUE, 7, 42.5);
        String json = mapper.writeValueAsString(value);
        assertThat(json).isEqualTo("{\"id\":\"9223372036854775807\",\"revision\":7,\"adherenceRate\":42.5}");
        assertThat(mapper.readValue(json, Sample.class)).isEqualTo(value);
    }

    @Test void rejectsNumericIdAndOverflow() {
        assertThatThrownBy(() -> mapper.readValue("{\"id\":9007199254740993}", Sample.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"id\":\"9223372036854775808\"}", Sample.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test void preservesNullableValueWithoutChoosingRequiredContract() throws Exception {
        assertThat(mapper.readValue("{\"id\":null}", Sample.class).id()).isNull();
        assertThat(mapper.writeValueAsString(new Sample(null, 0, 0))).contains("\"id\":null");
    }
}
