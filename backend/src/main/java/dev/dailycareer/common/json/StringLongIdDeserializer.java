package dev.dailycareer.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public final class StringLongIdDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        try {
            return Long.valueOf(parser.getText());
        } catch (NumberFormatException exception) {
            return (Long) context.handleWeirdStringValue(Long.class, parser.getText(), "Expected a bigint ID string");
        }
    }
}
