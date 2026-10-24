package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** One provider call: developer instructions, a strict JSON schema and the user-turn input as data. */
public record AiRequest(String instructions, String schemaName, JsonNode schema, String input, int maxOutputTokens) {}
