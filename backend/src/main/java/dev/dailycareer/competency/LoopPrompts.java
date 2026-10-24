package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.ai.AiRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Versioned prompts: the shared system rules plus one role file and its strict output schema. */
@Component
class LoopPrompts {
    static final String VERSION = "competency-loop-v1";

    enum Role {
        TASK_GENERATE("task_generate", 10_000), GRADING("grading", 4_000), FOLLOWUP_TURN("followup_turn", 4_000),
        CLAIM_REVIEW("claim_review", 3_000);
        final String file;
        final int maxOutputTokens;
        Role(String file, int maxOutputTokens) { this.file = file; this.maxOutputTokens = maxOutputTokens; }
    }

    private final Map<Role, String> instructions = new EnumMap<>(Role.class);
    private final Map<Role, JsonNode> schemas = new EnumMap<>(Role.class);

    LoopPrompts(ObjectMapper json) {
        String system = read("system.md");
        for (Role role : Role.values()) {
            instructions.put(role, system + "\n\n" + read(role.file + ".md"));
            try { schemas.put(role, json.readTree(read(role.file + ".schema.json"))); }
            catch (IOException invalid) { throw new UncheckedIOException(invalid); }
        }
    }

    AiRequest request(Role role, JsonNode input) {
        return new AiRequest(instructions.get(role), role.file, schemas.get(role), input.toString(), role.maxOutputTokens);
    }

    /** Fixed prompt overhead used when estimating the worst-case cost before queueing. */
    String overhead(Role role) { return instructions.get(role) + schemas.get(role); }

    private static String read(String name) {
        try { return new ClassPathResource("prompts/" + VERSION + "/" + name).getContentAsString(StandardCharsets.UTF_8); }
        catch (IOException missing) { throw new UncheckedIOException(missing); }
    }
}
