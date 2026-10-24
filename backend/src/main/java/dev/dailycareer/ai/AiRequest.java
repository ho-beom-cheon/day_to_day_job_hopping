package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One provider call: developer instructions, a strict JSON schema and the user-turn input as data.
 * reasoningEffort is the provider's reasoning.effort value; maxOutputTokens includes reasoning tokens.
 */
public record AiRequest(String instructions, String schemaName, JsonNode schema, String input, int maxOutputTokens,
                        String reasoningEffort) {}
