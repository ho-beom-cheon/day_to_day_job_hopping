package dev.dailycareer.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-side OpenAI configuration. The key is never exposed to clients or stored in the database. */
@Component
public class AiSettings {
    public static final String PROVIDER = "OPENAI";
    private final String providerType;
    private final String apiKey;
    private final String model;

    public AiSettings(@Value("${AI_PROVIDER_TYPE:}") String providerType, @Value("${OPENAI_API_KEY:}") String apiKey,
               @Value("${OPENAI_MODEL:gpt-6-luna}") String model) {
        this.providerType = providerType;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean enabled() { return "openai".equalsIgnoreCase(providerType) && !apiKey.isBlank() && !model.isBlank(); }
    public String model() { return model; }
    String apiKey() { return apiKey; }
}
