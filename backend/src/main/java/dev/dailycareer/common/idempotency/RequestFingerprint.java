package dev.dailycareer.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

public final class RequestFingerprint {
    private RequestFingerprint() {}

    /** Input includes validated path/query/body/preconditions, not just the body. Omission != null. */
    public static String digest(JsonNode input) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("Expected an explicit request fingerprint object");
        return HexFormat.of().formatHex(sha256(canonical(input).toString()));
    }

    static byte[] sha256(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node.deepCopy();
    }
}
